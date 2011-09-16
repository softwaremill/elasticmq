package org.elasticmq.storage

import org.elasticmq._

trait Storage {
  def queueStorage: QueueStorage
  def messageStorage: MessageStorage
}

trait QueueStorage {
  def persistQueue(queue: Queue)
  def updateQueue(queue: Queue)
  def deleteQueue(queue: Queue)
  def lookupQueue(name: String): Option[Queue]
  def listQueues: Seq[Queue]
}

trait MessageStorage {
  def persistMessage(message: SpecifiedMessage)
  def updateMessage(message: SpecifiedMessage)

  /**
   * Tries to update the given message with a new next delivery value. Will return the updated message if
   * the update succeedes, {@code None} otherwise. For the update to succeed, a message with the same id
   * and next delivery value must be found as in {@code message}; hence, next delivery is in fact an
   * optimistic lock.
   */
  def updateNextDelivery(message: SpecifiedMessage, nextDelivery: MillisNextDelivery): Option[SpecifiedMessage]
  def deleteMessage(message: AnyMessage)

  def lookupMessage(id: String): Option[SpecifiedMessage]
  def lookupPendingMessage(queue: Queue, deliveryTime: Long): Option[SpecifiedMessage]
}