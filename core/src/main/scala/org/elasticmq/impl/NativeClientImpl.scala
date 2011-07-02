package org.elasticmq.impl

import org.elasticmq.storage.Storage
import java.util.UUID
import org.elasticmq._

class NativeClientImpl(storage: Storage) extends Client {
  def queueClient = new NativeQueueClientImpl(storage)
  def messageClient = new NativeMessageClientImpl(storage)
}

class NativeQueueClientImpl(storage: Storage) extends QueueClient {
  def createQueue(queue: Queue) = {
    storage.queueStorage.persistQueue(queue)
    queue
  }

  def lookupQueue(name: String) = storage.queueStorage.lookupQueue(name)

  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: Long) = {
    val newQueue = queue.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
    storage.queueStorage.updateQueue(newQueue)
    newQueue
  }

  def deleteQueue(queue: Queue) {
    storage.queueStorage.deleteQueue(queue)
  }
}

class NativeMessageClientImpl(storage: Storage) extends MessageClient {
  def sendMessage(message: Message) = {
    var toSend = message
    if (toSend.id == null) toSend = toSend.copy(id = generateId())
    if (toSend.visibilityTimeout == null) toSend = toSend.copy(visibilityTimeout = message.queue.defaultVisibilityTimeout)
    storage.messageStorage.persistMessage(toSend)
    toSend
  }

  def receiveMessage(queue: Queue) = {
    null
  }

  def updateVisibilityTimeout(message: Message, newVisibilityTimeout: Long) = {
    val newMessage = message.copy(visibilityTimeout = newVisibilityTimeout)
    storage.messageStorage.updateMessage(newMessage)
    newMessage
  }

  def deleteMessage(message: Message) {
    storage.messageStorage.deleteMessage(message)
  }

  private def generateId(): String = UUID.randomUUID().toString
}