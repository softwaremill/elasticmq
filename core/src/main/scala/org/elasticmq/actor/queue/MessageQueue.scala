package org.elasticmq.actor.queue

import scala.annotation.tailrec
import scala.collection.mutable

sealed trait MessageQueue {

  /** Add a message onto the queue. Note that this doesn't do any deduplication, that should've happened in an earlier
    * step.
    *
    * @param message
    *   The message to add onto the queue
    */
  def +=(message: InternalMessage): Unit

  /** Get the messages indexed by their unique id
    *
    * @return
    *   The messages indexed by their id
    */
  def byId: Map[String, InternalMessage]

  /** Drop all messages on the queue
    */
  def clear(): Unit

  /** Remove the message with the given id
    *
    * @param messageId
    *   The id of the message to remove
    */
  def remove(messageId: String): Unit

  /** Return a message queue where all the messages on the queue do not match the given predicate function
    *
    * @param p
    *   The predicate function to filter the message by. Any message that does not match the predicate will be retained
    *   on the new queue
    * @return
    *   The new message queue
    */
  def filterNot(p: InternalMessage => Boolean): MessageQueue

  /** Dequeues `count` messages from the queue
    *
    * @param count
    *   The number of messages to dequeue from the queue
    * @param deliveryTime
    *   The timestamp from which messages should be available (usually, this is the current millis since epoch. It is
    *   useful to pass in a special value during the tests however.)
    * @return
    *   The dequeued messages, if any
    */
  def dequeue(count: Int, deliveryTime: Long): List[InternalMessage]

  /** Get the next available message on the given queue
    *
    * @param priorityQueue
    *   The queue for which to get the next available message. It's assumed the messages on this queue all belong to the
    *   same message group.
    * @param deliveryTime
    *   The timestamp from which messages should be available
    * @param accBatch
    *   An accumulator holding the messages that have already been retrieved.
    * @param accMessage
    *   An accumulator holding the messages that have been dequeued from the priority queue and cannot be delivered.
    *   These messages should be put back on the queue before returning to the caller
    * @return
    */
  @tailrec
  protected final def nextVisibleMessage(
      priorityQueue: mutable.PriorityQueue[InternalMessage],
      deliveryTime: Long,
      accBatch: List[InternalMessage],
      accMessage: Seq[InternalMessage] = Seq.empty
  ): Option[InternalMessage] = {
    if (priorityQueue.nonEmpty) {
      val msg = priorityQueue.dequeue()

      if (byId.get(msg.id).isEmpty) {
        // A message that's not in the byId map is considered to be deleted and can be dropped
        nextVisibleMessage(priorityQueue, deliveryTime, accBatch, accMessage)
      } else {

        lazy val isInBatch = accBatch.exists(_.id == msg.id)
        lazy val isInLocalAcc = accMessage.exists(_.id == msg.id)
        if (msg.deliverable(deliveryTime) && !isInLocalAcc && !isInBatch) {
          // If this message is deliverable, we put all the previously dequeued (but undeliverable) messages back on
          // the queue and return this message for delivery
          priorityQueue ++= accMessage
          Some(msg)
        } else {
          // The message is not deliverable. Put it and all the other previously retrieved messages in this batch back
          // on the priority queue.
          priorityQueue += msg
          priorityQueue ++= accMessage
          None
        }
      }
    } else {
      // If the priority queue is empty, there are no further messages to test. Put any dequeued but unavailable
      // messages back on the queue and return a None
      priorityQueue ++= accMessage
      None
    }
  }
}

object MessageQueue {

  def apply(isFifo: Boolean): MessageQueue =
    if (isFifo) {
      new FifoMessageQueue
    } else {
      new SimpleMessageQueue
    }

  /** A "simple" straightforward message queue. The queue represents the common SQS behaviour
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

    override def remove(messageId: String): Unit = messagesById.remove(messageId)

    override def filterNot(p: InternalMessage => Boolean): MessageQueue = {
      val newMessageQueue = new SimpleMessageQueue
      messagesById
        .filterNot { case (_, msg) => p(msg) }
        .foreach { case (_, msg) => newMessageQueue += msg }
      newMessageQueue
    }

    def dequeue(count: Int, deliveryTime: Long): List[InternalMessage] = {
      dequeue0(count, deliveryTime, List.empty)
    }

    @tailrec
    private def dequeue0(count: Int, deliveryTime: Long, acc: List[InternalMessage]): List[InternalMessage] = {
      if (count == 0) {
        acc
      } else {
        nextVisibleMessage(messageQueue, deliveryTime, acc) match {
          case Some(msg) => dequeue0(count - 1, deliveryTime, acc :+ msg)
          case None      => acc
        }
      }
    }
  }

  /** A FIFO queue that mimics SQS' FIFO queue implementation
    */
  class FifoMessageQueue extends SimpleMessageQueue {
    private val messagesbyMessageGroupId = mutable.HashMap.empty[String, mutable.PriorityQueue[InternalMessage]]

    override def +=(message: InternalMessage): Unit = {
      messagesById += message.id -> message
      val messageGroupId = getMessageGroupIdUnsafe(message)
      val groupMessages = messagesbyMessageGroupId.getOrElseUpdate(messageGroupId, mutable.PriorityQueue.empty)
      messagesbyMessageGroupId.put(messageGroupId, groupMessages += message)
    }

    override def clear(): Unit = {
      super.clear()
      messagesbyMessageGroupId.clear()
    }

    override def remove(messageId: String): Unit = {
      messagesById.remove(messageId).foreach { msg =>
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

    override def dequeue(count: Int, deliveryTime: Long): List[InternalMessage] = {
      dequeue0(count, deliveryTime, List.empty)
    }

    private def dequeue0(count: Int, deliveryTime: Long, acc: List[InternalMessage]): List[InternalMessage] = {
      if (count == 0) {
        acc
      } else {
        dequeueFromFifo(acc, deliveryTime) match {
          case Some(msg) => dequeue0(count - 1, deliveryTime, acc :+ msg)
          case None      => acc
        }
      }
    }

    /** Dequeue a message from the fifo queue. Try to dequeue a message from the same message group as the previous
      * message before trying other message groups.
      */
    private def dequeueFromFifo(
        accBatch: List[InternalMessage],
        deliveryTime: Long,
        triedMessageGroups: Set[String] = Set.empty
    ): Option[InternalMessage] = {
      val messageGroupIdHint = accBatch.lastOption.map(getMessageGroupIdUnsafe).filterNot(triedMessageGroups.contains)
      messageGroupIdHint.orElse(randomMessageGroup(triedMessageGroups)).flatMap { messageGroupId =>
        dequeueFromMessageGroup(messageGroupId, deliveryTime, accBatch)
          .orElse(dequeueFromFifo(accBatch, deliveryTime, triedMessageGroups + messageGroupId))
      }
    }

    /** Try to dequeue a message from the given message group
      */
    private def dequeueFromMessageGroup(
        messageGroupId: String,
        deliveryTime: Long,
        accBatch: List[InternalMessage]
    ): Option[InternalMessage] = {
      messagesbyMessageGroupId.get(messageGroupId) match {
        case Some(priorityQueue) if priorityQueue.nonEmpty =>
          val msg = nextVisibleMessage(priorityQueue, deliveryTime, accBatch)
          if (priorityQueue.isEmpty) {
            messagesbyMessageGroupId.remove(messageGroupId)
          } else {
            messagesbyMessageGroupId += messageGroupId -> priorityQueue
          }
          msg
        case _ => None
      }
    }

    /** Return a message group id that has at least 1 message active on the queue and that is not part of the given set
      * of `triedMessageGroupIds`
      *
      * @param triedMessageGroupIds
      *   The ids of message groups to ignore
      * @return
      *   The id of a random message group that is not part of `triedMessageGroupIds`
      */
    private def randomMessageGroup(triedMessageGroupIds: Set[String]): Option[String] = {
      val remainingMessageGroupIds = messagesbyMessageGroupId.keySet -- triedMessageGroupIds
      remainingMessageGroupIds.headOption
    }

    /** Get the message group id from a given message. If the message has no message group id, an
      * [[IllegalStateException]] will be thrown.
      *
      * @param msg
      *   The message to get the message group id for
      * @return
      *   The message group id
      * @throws IllegalStateException
      *   if the message has no message group id
      */
    private def getMessageGroupIdUnsafe(msg: InternalMessage): String =
      getMessageGroupIdUnsafe(msg.messageGroupId)

    /** Get the message group id from an optional string. If the given optional string is empty, an
      * [[IllegalStateException]] will be thrown
      *
      * @param messageGroupId
      *   The optional string
      * @return
      *   The message group id
      * @throws IllegalStateException
      *   if the optional string holds no message group id
      */
    private def getMessageGroupIdUnsafe(messageGroupId: Option[String]) =
      messageGroupId.getOrElse(
        throw new IllegalStateException("Messages on a FIFO queue are required to have a message group id")
      )
  }
}
