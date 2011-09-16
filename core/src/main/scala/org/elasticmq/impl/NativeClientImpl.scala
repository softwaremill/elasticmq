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

  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: VisibilityTimeout) = {
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
  def sendMessage(message: AnyMessage) = {
    var toSend = message
    if (toSend.id == null) toSend = toSend.copy(id = generateId())
    val toSendWithDelivery = toSend.nextDelivery match {
      case ImmediateNextDelivery => toSend.copy(nextDelivery = nextDelivery(toSend))
      case m: MillisNextDelivery => toSend.copy(nextDelivery = m)
    }

    storage.messageStorage.persistMessage(toSendWithDelivery)
    toSendWithDelivery
  }

  def receiveMessage(queue: Queue): Option[SpecifiedMessage] = {
    val now = (new DateTime).getMillis

    storage.messageStorage.lookupPendingMessage(queue, now)
      .flatMap(message =>
        storage.messageStorage
                .updateNextDelivery(message, nextDelivery(message))
                .orElse(receiveMessage(queue)))
  }

  def updateVisibilityTimeout(message: AnyMessage, newVisibilityTimeout: VisibilityTimeout) = {
    val newMessage = message.copy(nextDelivery = nextDelivery(message, newVisibilityTimeout.millis))
    storage.messageStorage.updateMessage(newMessage)
    newMessage
  }

  def deleteMessage(message: AnyMessage) {
    storage.messageStorage.deleteMessage(message)
  }

  def lookupMessage(id: String) = {
    storage.messageStorage.lookupMessage(id)
  }

  private def generateId(): String = UUID.randomUUID().toString

  private def nextDelivery(m: AnyMessage): MillisNextDelivery = {
    nextDelivery(m, m.queue.defaultVisibilityTimeout.millis)
  }

  private def nextDelivery(m: AnyMessage, delta: Long): MillisNextDelivery = {
    val now = (new DateTime).getMillis
    MillisNextDelivery(now + delta)
  }
}