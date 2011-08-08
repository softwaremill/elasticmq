package org.elasticmq.impl

import org.elasticmq.storage.Storage
import java.util.UUID
import org.elasticmq._
import org.joda.time.DateTime

class NativeClientImpl(storage: Storage) extends Client {
  def queueClient: QueueClient = new NativeQueueClientImpl(storage)
  def messageClient: MessageClient = new NativeMessageClientImpl(storage)
}

class NativeQueueClientImpl(storage: Storage) extends QueueClient {
  def createQueue(queue: Queue) = {
    storage.queueStorage.persistQueue(queue)
    queue
  }

  def lookupQueue(name: String) = storage.queueStorage.lookupQueue(name)

  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: MillisVisibilityTimeout) = {
    val newQueue = queue.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
    storage.queueStorage.updateQueue(newQueue)
    newQueue
  }

  def deleteQueue(queue: Queue) {
    storage.queueStorage.deleteQueue(queue)
  }

  def listQueues = storage.queueStorage.listQueues
}

class NativeMessageClientImpl(storage: Storage) extends MessageClient {
  def sendMessage(message: Message) = {
    var toSend = message
    if (toSend.id == null) toSend = toSend.copy(id = generateId())
    if (toSend.visibilityTimeout == DefaultVisibilityTimeout) toSend = toSend.copy(visibilityTimeout = message.queue.defaultVisibilityTimeout)
    storage.messageStorage.persistMessage(toSend)
    toSend
  }

  def receiveMessage(queue: Queue): Option[Message] = {
    val now = (new DateTime).getMillis

    storage.messageStorage.lookupPendingMessage(queue, now)
      .flatMap(message =>
        storage.messageStorage
                .updateLastDelivered(message, now)
                .orElse(receiveMessage(queue)))
  }

  def updateVisibilityTimeout(message: Message, newVisibilityTimeout: MillisVisibilityTimeout) = {
    val newMessage = message.copy(visibilityTimeout = newVisibilityTimeout)
    storage.messageStorage.updateMessage(newMessage)
    newMessage
  }

  def deleteMessage(message: Message) {
    storage.messageStorage.deleteMessage(message)
  }

  private def generateId(): String = UUID.randomUUID().toString
}