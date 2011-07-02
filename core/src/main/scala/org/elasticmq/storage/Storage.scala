package org.elasticmq.storage

import org.elasticmq.{Queue, Message}

trait Storage {
  def queueStorage: QueueStorage
  def messageStorage: MessageStorage
}

trait QueueStorage {
  def persistQueue(queue: Queue)
  def updateQueue(queue: Queue)
  def deleteQueue(queue: Queue)
  def lookupQueue(name: String): Option[Queue]
}

trait MessageStorage {
  def persistMessage(message: Message)
  def updateMessage(message: Message)

  /**
   * Tries to update the given message with a new last delivered value. Will return the updated message if
   * the update succeedes, {@code None} otherwise. For the update to succeed, a message with the same id
   * and last delivered value must be found as in {@code message}; hence, last delivered is in fact an
   * optimistic lock.
   */
  def updateLastDelivered(message: Message, lastDelivered: Long): Option[Message]
  def deleteMessage(message: Message)

  def lookupMessage(id: String): Option[Message]
  def lookupPendingMessage(queue: Queue, deliveryTime: Long): Option[Message]
}