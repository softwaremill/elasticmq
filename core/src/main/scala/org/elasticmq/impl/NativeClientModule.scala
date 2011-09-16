package org.elasticmq.impl

import java.util.UUID
import org.elasticmq._
import org.joda.time.DateTime
import org.elasticmq.storage.{QueueStorageModule, MessageStorageModule}

trait NativeClientModule {
  this: MessageStorageModule with QueueStorageModule with NowModule =>

  def nativeClient: Client = nativeClientImpl

  object nativeClientImpl extends Client {
    def queueClient: QueueClient = nativeQueueClientImpl
    def messageClient: MessageClient = nativeMessageClientImpl
  }

  object nativeQueueClientImpl extends QueueClient {
    def createQueue(queue: Queue) = {
      queueStorage.persistQueue(queue)
      queue
    }

    def lookupQueue(name: String) = queueStorage.lookupQueue(name)

    def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: VisibilityTimeout) = {
      val newQueue = queue.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
      queueStorage.updateQueue(newQueue)
      newQueue
    }

    def deleteQueue(queue: Queue) {
      queueStorage.deleteQueue(queue)
    }

    def listQueues = queueStorage.listQueues
  }

  object nativeMessageClientImpl extends MessageClient {
    def sendMessage(message: AnyMessage) = {
      var toSend = message
      if (toSend.id == null) toSend = toSend.copy(id = generateId())
      val toSendWithDelivery = toSend.nextDelivery match {
        case ImmediateNextDelivery => toSend.copy(nextDelivery = nextDelivery(toSend))
        case m: MillisNextDelivery => toSend.copy(nextDelivery = m)
      }

      messageStorage.persistMessage(toSendWithDelivery)
      toSendWithDelivery
    }

    def receiveMessage(queue: Queue): Option[SpecifiedMessage] = {
      val now = (new DateTime).getMillis

      messageStorage.lookupPendingMessage(queue, now)
              .flatMap(message =>
        messageStorage
                .updateNextDelivery(message, nextDelivery(message))
                .orElse(receiveMessage(queue)))
    }

    def updateVisibilityTimeout(message: AnyMessage, newVisibilityTimeout: VisibilityTimeout) = {
      val newMessage = message.copy(nextDelivery = nextDelivery(message, newVisibilityTimeout.millis))
      messageStorage.updateMessage(newMessage)
      newMessage
    }

    def deleteMessage(message: AnyMessage) {
      messageStorage.deleteMessage(message)
    }

    def lookupMessage(id: String) = {
      messageStorage.lookupMessage(id)
    }

    private def generateId(): String = UUID.randomUUID().toString

    private def nextDelivery(m: AnyMessage): MillisNextDelivery = {
      nextDelivery(m, m.queue.defaultVisibilityTimeout.millis)
    }

    private def nextDelivery(m: AnyMessage, delta: Long): MillisNextDelivery = {
      MillisNextDelivery(now + delta)
    }
  }
}