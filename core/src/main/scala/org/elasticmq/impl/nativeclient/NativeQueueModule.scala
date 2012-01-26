package org.elasticmq.impl.nativeclient

import org.elasticmq._
import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule
import org.elasticmq.impl.{MessageData, QueueData, NowModule}
import org.elasticmq.storage.StorageModule
import org.joda.time.Duration
import java.util.UUID

trait NativeQueueModule {
  this: StorageModule with NativeMessageModule with NativeHelpersModule
    with NowModule with VolatileTaskSchedulerModule =>

  class NativeQueue(val name: String) extends Queue with WithLazyAtomicData[QueueData] {
    def this(queueData: QueueData) = {
      this(queueData.name)
      data = queueData
    }

    def initData = queueStorage.lookupQueue(name)
      .getOrElse(throw new QueueDoesNotExistException(name))

    // Operations

    def sendMessage(messageBuilder: MessageBuilder) = {
      val messageId = messageBuilder.id match {
        case None => generateId()
        case Some(id) => id
      }

      val nextDelivery = messageBuilder.nextDelivery match {
        case ImmediateNextDelivery => MillisNextDelivery(now + data.delay.getMillis)
        case a: AfterMillisNextDelivery => MillisNextDelivery(now + a.millis)
        case m: MillisNextDelivery => m
      }

      val message = MessageData(messageId, messageBuilder.content, nextDelivery, nowAsDateTime)

      messageStorage(name).persistMessage(message)
      new NativeMessage(name, message)
    }

    def receiveMessage(visibilityTimeout: VisibilityTimeout) = {
      val messageOption = doReceiveMessage(visibilityTimeout)

      messageOption.foreach(message => volatileTaskScheduler.schedule {
        val statsStorage = messageStatisticsStorage(name)
        val stats = statsStorage.readMessageStatistics(message.id)
        val bumpedStats = bumpMessageStatistics(stats)
        statsStorage.writeMessageStatistics(message.id, bumpedStats)
      })

      messageOption.map(new NativeMessage(name, _))
    }

    def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout) = {
      val messageOption = doReceiveMessage(visibilityTimeout)

      messageOption.map(message => {
        val statsStorage = messageStatisticsStorage(name)
        val stats = statsStorage.readMessageStatistics(message.id)
        val bumpedStats = bumpMessageStatistics(stats)
        volatileTaskScheduler.schedule {
          statsStorage.writeMessageStatistics(message.id, bumpedStats)
        }

        (new NativeMessage(name, message), bumpedStats)
      })
    }

    private def doReceiveMessage(visibilityTimeout: VisibilityTimeout) = {
      val newNextDelivery = visibilityTimeout match {
        case DefaultVisibilityTimeout => defaultNextDelivery()
        case MillisVisibilityTimeout(millis) => computeNextDelivery(millis)
      }

      messageStorage(name).receiveMessage(now, newNextDelivery)
    }

    def lookupMessage(messageId: MessageId) = {
      messageStorage(name).lookupMessage(messageId).map(new NativeMessage(name, _))
    }

    def updateDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout): Queue = {
      queueStorage.updateQueue(data.copy(defaultVisibilityTimeout = defaultVisibilityTimeout))
      fetchQueue()
    }

    def updateDelay(delay: Duration): Queue = {
      queueStorage.updateQueue(data.copy(delay = delay))
      fetchQueue()
    }

    def fetchStatistics() = queueStorage.queueStatistics(name, now)

    def delete() {
      queueStorage.deleteQueue(name)
    }

    def fetchQueue() = {
      clearData()
      this
    }

    def messageOperations(id: MessageId) = new NativeMessage(name, id)

    // Queue

    def defaultVisibilityTimeout = data.defaultVisibilityTimeout

    def delay = data.delay

    def created = data.created

    def lastModified = data.lastModified

    // Other

    private def bumpMessageStatistics(currentMessageStatistics: MessageStatistics) = {
      currentMessageStatistics.approximateFirstReceive match {
        case NeverReceived => MessageStatistics(OnDateTimeReceived(nowAsDateTime), 1)
        case received => MessageStatistics(currentMessageStatistics.approximateFirstReceive,
          currentMessageStatistics.approximateReceiveCount + 1)
      }
    }

    private def defaultNextDelivery(): MillisNextDelivery = {
      computeNextDelivery(data.defaultVisibilityTimeout.millis)
    }

    private def generateId() = MessageId(UUID.randomUUID().toString)
  }
}