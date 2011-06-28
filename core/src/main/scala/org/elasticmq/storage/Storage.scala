package org.elasticmq.storage

import org.elasticmq.{Queue, Message}

trait Storage {
  def queueStorage: QueueStorage
  def messageStorage: MessageStorage
}

trait QueueStorage {
  def persistQueue(queue: Queue)
  def updateQueue(queue: Queue)
  def removeQueue(queue: Queue)
  def lookupQueue(name: String): Option[Queue]
}

trait MessageStorage {
  def persistMessage(message: Message)
  def updateMessage(message: Message)
  def removeMessage(message: Message)

  def lookupMessage(id: String): Option[Message]
  def lookupPendingMessage(queue: Queue, deliveredBefore: Long): Option[Message]
}