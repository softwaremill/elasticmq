package org.elasticmq.actor.queue

import scala.annotation.tailrec
import scala.collection.mutable

import org.elasticmq.MessageData


sealed trait MessageQueue {

  /**
   * Add a message onto the queue. Note that this doesn't do any deduplication, that should've happened in an earlier
   * step.
   *
   * @param message    The message to add onto the queue
   */
  def +=(message: InternalMessage): Unit

  /**
   * Get the messages indexed by their unique id
   *
   * @return    The messages indexed by their id
   */
  def byId: Map[String, InternalMessage]

  /**
   * Drop all messages on the queue
   */
  def clear(): Unit

  /**
   * @return    Whether there are no messages on the queue
   */
  def isEmpty: Boolean

  /**
   * Remove the message with the given id
   *
   * @param messageId    The id of the message to remove
   */
  def remove(messageId: String): Unit

  /**
   * Return a message queue where all the messages on the queue do not match the given predicate function
   *
   * @param p    The predicate function to filter the message by. Any message that does not match the predicate will be
   *             retained on the new queue
   * @return     The new message queue
   */
  def filterNot(p: InternalMessage => Boolean): MessageQueue

  /**
   * Dequeue a message from the queue
   *
   * @param accBatch    The messages that have been dequeued in the current batch
   * @return            A message that can be dequeued
   * @todo              Refactor this so that multiple messages can be asked for and the caller doesn't have to maintain
   *                    an accumulator
   */
  def dequeue(accBatch: List[MessageData] = List.empty): Option[InternalMessage]
}

object MessageQueue {

  def apply(isFifo: Boolean): MessageQueue = if (isFifo) {
    new FifoMessageQueue
  } else {
    new SimpleMessageQueue
  }

  /**
   * A "simple" straightforward message queue. The queue represents the common SQS behaviour
   */
  class SimpleMessageQueue extends MessageQueue {
    protected val messagesById: mutable.HashMap[String, InternalMessage] = mutable.HashMap.empty
    protected val messageQueue: mutable.PriorityQueue[InternalMessage] = mutable.PriorityQueue.empty

    override def +=(message: InternalMessage): Unit = {
      messagesById += message.id -> message
      messageQueue += message
    }

    override def byId: Map[String, InternalMessage] = messagesById.toMap

    override def clear(): Unit = {
      messagesById.clear()
      messageQueue.clear()
    }

    override def isEmpty: Boolean = messageQueue.isEmpty

    override def remove(messageId: String): Unit = messagesById.remove(messageId)

    override def filterNot(p: InternalMessage => Boolean): MessageQueue = {
      val newMessageQueue = new SimpleMessageQueue
      messagesById
        .filterNot { case (_, msg) => p(msg) }
        .foreach { case (_, msg) => newMessageQueue += msg }
      newMessageQueue
    }

    override def dequeue(accBatch: List[MessageData]): Option[InternalMessage] = if (!isEmpty) {
      Some(messageQueue.dequeue())
    } else {
      Option.empty
    }
  }

  /**
   * A FIFO queue that mimics SQS' FIFO queue implementation
   */
  class FifoMessageQueue extends SimpleMessageQueue {
    private val messagesbyMessageGroupId = mutable.HashMap.empty[String, mutable.PriorityQueue[InternalMessage]]

    override def +=(message: InternalMessage): Unit = {
      messagesById += message.id -> message
      val messageGroupId = getMessageGroupIdUnsafe(message)
      val groupMessages = messagesbyMessageGroupId.getOrElseUpdate(messageGroupId, mutable.PriorityQueue.empty)
      messagesbyMessageGroupId.put(messageGroupId, groupMessages += message)
    }

    override def isEmpty: Boolean = messagesById.isEmpty

    override def clear(): Unit = {
      super.clear()
      messagesbyMessageGroupId.clear()
    }

    override def remove(messageId: String): Unit = {
      messagesById.get(messageId).foreach { msg =>
        val messageGroupId = getMessageGroupIdUnsafe(msg)
        messagesbyMessageGroupId.get(messageGroupId).foreach { prioQueue =>
          val newQueue = prioQueue.filterNot(_.id == messageId)
          if (newQueue.nonEmpty) {
            messagesbyMessageGroupId.put(messageGroupId, newQueue)
          } else {
            messagesbyMessageGroupId.remove(messageGroupId)
          }
        }
      }
    }

    override def filterNot(p: InternalMessage => Boolean): MessageQueue = {
      val newFifoQueue = new FifoMessageQueue
      messagesById.filterNot { case (_, msg) => p(msg) }.foreach { case (_, msg) => newFifoQueue += msg }
      newFifoQueue
    }

    override def dequeue(accBatch: List[MessageData]): Option[InternalMessage] = if (!isEmpty) {
      dequeueFromFifo(accBatch)
    } else {
      Option.empty
    }

    /**
     * Dequeue a message from the fifo queue. Try to dequeue a message from the same message group as the previous
     * message before trying other message groups.
     */
    private def dequeueFromFifo(accBatch: List[MessageData],
        triedMessageGroups: Set[String] = Set.empty): Option[InternalMessage] = {
      val messageGroupIdHint = accBatch.lastOption.map(getMessageGroupIdUnsafe).filterNot(triedMessageGroups.contains)
      messageGroupIdHint.orElse(randomMessageGroup(triedMessageGroups)).flatMap { messageGroupId =>
        dequeueFromMessageGroup(messageGroupId, accBatch)
          .orElse(dequeueFromFifo(accBatch, triedMessageGroups + messageGroupId))
      }
    }

    /**
     * Try to dequeue a message from the given message group
     */
    private def dequeueFromMessageGroup(messageGroupId: String, accBatch: List[MessageData]): Option[InternalMessage] = {
      messagesbyMessageGroupId.get(messageGroupId) match {
        case Some(priorityQueue) if priorityQueue.nonEmpty =>
          val msg = nextVisibleMessage(priorityQueue, accBatch)
          if (priorityQueue.isEmpty) {
            messagesbyMessageGroupId.remove(messageGroupId)
          } else {
            messagesbyMessageGroupId += messageGroupId -> priorityQueue
          }
          msg
        case _ => None
      }
    }

    @tailrec
    private def nextVisibleMessage(priorityQueue: mutable.PriorityQueue[InternalMessage],
        accBatch: List[MessageData], accMessage: Seq[InternalMessage] = Seq.empty): Option[InternalMessage] = {
      if (priorityQueue.nonEmpty) {
        val msg = priorityQueue.dequeue()
        if (msg.deliverable(System.currentTimeMillis())) {
          priorityQueue ++= accMessage
          Some(msg)
        } else if (accBatch.exists(_.id.id == msg.id)) {
          // If the message is invisible, we can only continue is the message is part of the current batch as we don't
          // want to return any other message in this message group as long as it's not been handled
          nextVisibleMessage(priorityQueue, accBatch, accMessage :+ msg)
        } else {
          priorityQueue += msg
          priorityQueue ++= accMessage
          None
        }
      } else {
        priorityQueue ++= accMessage
        None
      }
    }

    private def randomMessageGroup(triedMessageGroups: Set[String]): Option[String] = {
      val remainingMessageGroupIds = messagesbyMessageGroupId.keySet -- triedMessageGroups
      remainingMessageGroupIds.headOption
    }

    /**
     * Get the message group id from a given message. If the message has no message group id, an
     * [[IllegalStateException]] will be thrown.
     *
     * @param msg    The message to get the message group id for
     * @return       The message group id
     * @throws       IllegalStateException if the message has no message group id
     */
    private def getMessageGroupIdUnsafe(msg: InternalMessage): String =
      getMessageGroupIdUnsafe(msg.messageGroupId)

    /**
     * Get the message group id from the given message data. If the message data has no message group id, an
     * [[IllegalStateException]] will be thrown.
     *
     * @param msgData    The message data to get the message group id for
     * @return           The message group id
     * @throws           IllegalStateException if the message data has no message group id
     */
    private def getMessageGroupIdUnsafe(msgData: MessageData): String =
      getMessageGroupIdUnsafe(msgData.messageGroupId)

    /**
     * Get the message group id from an optional string. If the given optional string is empty, an
     * [[IllegalStateException]] will be thrown
     *
     * @param messageGroupId    The optional string
     * @return                  The message group id
     * @throws                  IllegalStateException if the optional string holds no message group id
     */
    private def getMessageGroupIdUnsafe(messageGroupId: Option[String]) =
      messageGroupId.getOrElse(throw new IllegalStateException(
        "Messages on a FIFO queue are required to have a message group id"))
  }
}
