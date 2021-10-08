package org.elasticmq.actor.queue

import scala.collection.mutable

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
        case None => acc
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
    * The ids of message groups to ignore
    * @return
    * The id of a random message group that is not part of `triedMessageGroupIds`
    */
  private def randomMessageGroup(triedMessageGroupIds: Set[String]): Option[String] = {
    val remainingMessageGroupIds = messagesbyMessageGroupId.keySet -- triedMessageGroupIds
    remainingMessageGroupIds.headOption
  }

  /** Get the message group id from a given message. If the message has no message group id, an
    * [[IllegalStateException]] will be thrown.
    *
    * @param msg
    * The message to get the message group id for
    * @return
    * The message group id
    * @throws IllegalStateException
    * if the message has no message group id
    */
  private def getMessageGroupIdUnsafe(msg: InternalMessage): String =
    getMessageGroupIdUnsafe(msg.messageGroupId)

  /** Get the message group id from an optional string. If the given optional string is empty, an
    * [[IllegalStateException]] will be thrown
    *
    * @param messageGroupId
    * The optional string
    * @return
    * The message group id
    * @throws IllegalStateException
    * if the optional string holds no message group id
    */
  private def getMessageGroupIdUnsafe(messageGroupId: Option[String]) =
    messageGroupId.getOrElse(
      throw new IllegalStateException("Messages on a FIFO queue are required to have a message group id")
    )
}
