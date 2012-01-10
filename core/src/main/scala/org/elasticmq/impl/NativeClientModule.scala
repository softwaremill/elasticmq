package org.elasticmq.impl

import java.util.UUID
import org.elasticmq.storage.{MessageStatisticsStorageModule, QueueStorageModule, MessageStorageModule}
import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule
import org.elasticmq._
import org.joda.time.Duration

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

    def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: MillisVisibilityTimeout) = {
      val newQueue = queue.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
      val newQueueLastModified = updateQueueLastModified(newQueue)
      queueStorage.updateQueue(newQueueLastModified)
      newQueue
    }

    def updateDelay(queue: Queue, newDelay: Duration) = {
      val newQueue = queue.copy(delay = newDelay)
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
        case ImmediateNextDelivery => messageWithId.copy(nextDelivery = MillisNextDelivery(now + messageWithId.queue.delay.getMillis))
        case a: AfterMillisNextDelivery => messageWithId.copy(nextDelivery = MillisNextDelivery(now + a.millis))
        case m: MillisNextDelivery => messageWithId.copy(nextDelivery = m)
      }

      val messageWithCreatedDate = messageWithDelivery.copy(created = nowAsDateTime)

      messageStorage.persistMessage(messageWithCreatedDate)
      messageWithCreatedDate
    }

    def receiveMessage(queue: Queue, visibilityTimeout: VisibilityTimeout) = {
      val messageOption = doReceiveMessage(queue, visibilityTimeout)
      messageOption.foreach(message => volatileTaskScheduler.schedule {
        val stats = messageStatisticsStorage.readMessageStatistics(message)
        doBumpStatistics(stats)
      })
      messageOption
    }

    def receiveMessageWithStatistics(queue: Queue, visibilityTimeout: VisibilityTimeout) = {
      val message = doReceiveMessage(queue, visibilityTimeout)
      val stats = message.map(messageStatisticsStorage.readMessageStatistics(_))
      stats.foreach(s => volatileTaskScheduler.schedule {
        doBumpStatistics(s)
      })
      stats
    }

    private def doReceiveMessage(queue: Queue, visibilityTimeout: VisibilityTimeout) = {
      val newNextDelivery = visibilityTimeout match {
        case DefaultVisibilityTimeout => nextDelivery(queue)
        case MillisVisibilityTimeout(millis) => nextDelivery(millis)
      }

      messageStorage.receiveMessage(queue, now, newNextDelivery)
    }

    private def doBumpStatistics(stats: MessageStatistics) {
      val bumpedStats = bumpMessageStatistics(stats)
      messageStatisticsStorage.writeMessageStatistics(bumpedStats)
    }

    def updateVisibilityTimeout(message: IdentifiableMessage, newVisibilityTimeout: MillisVisibilityTimeout) = {
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

    def messageStatistics(message: SpecifiedMessage) = {
      messageStatisticsStorage.readMessageStatistics(message)
    }

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