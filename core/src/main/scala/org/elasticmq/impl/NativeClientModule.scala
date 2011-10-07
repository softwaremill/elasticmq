package org.elasticmq.impl

import java.util.UUID
import org.elasticmq._
import org.elasticmq.storage.{MessageStatisticsStorageModule, QueueStorageModule, MessageStorageModule}
import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule
import org.joda.time.DateTime

trait NativeClientModule {
  this: MessageStorageModule with QueueStorageModule with MessageStatisticsStorageModule
          with VolatileTaskSchedulerModule with NowModule =>

  def nativeClient: Client = nativeClientImpl

  object nativeClientImpl extends Client {
    def queueClient: QueueClient = nativeQueueClientImpl
    def messageClient: MessageClient = nativeMessageClientImpl
  }

  object nativeQueueClientImpl extends QueueClient {
    def createQueue(queue: Queue) = {
      val queueWithDates = updateQueueLastModified(queue.copy(created = nowAsDateTime))
      queueStorage.persistQueue(queueWithDates)
      queueWithDates
    }

    def lookupQueue(name: String) = queueStorage.lookupQueue(name)

    def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: VisibilityTimeout) = {
      val newQueue = queue.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
      val newQueueLastModified = updateQueueLastModified(newQueue)
      queueStorage.updateQueue(newQueueLastModified)
      newQueue
    }

    def deleteQueue(queue: Queue) {
      queueStorage.deleteQueue(queue)
    }

    def listQueues = queueStorage.listQueues

    def queueStatistics(queue: Queue) = queueStorage.queueStatistics(queue, now)

    private def updateQueueLastModified(queue: Queue) = queue.copy(lastModified = nowAsDateTime)
  }

  object nativeMessageClientImpl extends MessageClient {
    def sendMessage(message: AnyMessage) = {
      val messageWithId = message.id match {
        case None => message.copy(id = Some(generateId()))
        case id: Some[String] => message.copy(id = id)
      }

      val messageWithDelivery = messageWithId.nextDelivery match {
        case ImmediateNextDelivery => messageWithId.copy(nextDelivery = MillisNextDelivery(now))
        case m: MillisNextDelivery => messageWithId.copy(nextDelivery = m)
      }

      val messageWithCreatedDate = messageWithDelivery.copy(created = nowAsDateTime)

      messageStorage.persistMessage(messageWithCreatedDate)
      messageWithCreatedDate
    }

    def receiveMessage(queue: Queue): Option[SpecifiedMessage] = {
      val message = messageStorage.receiveMessage(queue, now, nextDelivery(queue))
      message.foreach(m => volatileTaskScheduler.schedule {
        val stats = messageStatisticsStorage.readMessageStatistics(m)
        val bumpedStats = bumpMessageStatistics(stats)
        messageStatisticsStorage.writeMessageStatistics(bumpedStats)
      })
      message
    }

    def updateVisibilityTimeout(message: IdentifiableMessage, newVisibilityTimeout: VisibilityTimeout) = {
      val newMessage = message.copy(nextDelivery = nextDelivery(newVisibilityTimeout.millis))
      messageStorage.updateMessage(newMessage)
      newMessage
    }

    def deleteMessage(message: IdentifiableMessage) {
      messageStorage.deleteMessage(message)
    }

    def lookupMessage(id: String) = {
      messageStorage.lookupMessage(id)
    }

    def messageStatistics(message: IdentifiableMessage) = messageStatisticsStorage.readMessageStatistics(message)

    def bumpMessageStatistics(currentMessageStatistics: MessageStatistics) = {
      val message = currentMessageStatistics.message
      currentMessageStatistics.approximateFirstReceive match {
        case NeverReceived => MessageStatistics(message, OnDateTimeReceived(nowAsDateTime), 1)
        case received => MessageStatistics(message, currentMessageStatistics.approximateFirstReceive,
          currentMessageStatistics.approximateReceiveCount + 1)
      }
    }

    private def generateId(): String = UUID.randomUUID().toString

    private def nextDelivery(queue: Queue): MillisNextDelivery = {
      nextDelivery(queue.defaultVisibilityTimeout.millis)
    }

    private def nextDelivery(delta: Long): MillisNextDelivery = {
      MillisNextDelivery(now + delta)
    }
  }
}