package org.elasticmq.actor.queue

import org.elasticmq.util.Logging

import scala.annotation.tailrec
import scala.collection.mutable

/** A "simple" straightforward message queue. The queue represents the common SQS behaviour
  */
class SimpleMessageQueue extends MessageQueue with Logging {
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
