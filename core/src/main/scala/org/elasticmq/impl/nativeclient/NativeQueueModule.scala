package org.elasticmq.impl.nativeclient

import org.elasticmq._
import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule
import org.elasticmq.impl.{MessageData, QueueData, NowModule}
import org.elasticmq.storage.StorageModule
import org.joda.time.Duration
import java.util.UUID
import com.weiglewilczek.slf4s.Logging

trait NativeQueueModule {
  this: StorageModule with NativeMessageModule with NativeHelpersModule
    with NowModule with VolatileTaskSchedulerModule =>

  class NativeQueue(queueName: String) extends Queue with WithLazyAtomicData[QueueData] with Logging {
    def this(queueData: QueueData) = {
      this(queueData.name)
      data = queueData
    }

    def initData = queueStorage.lookupQueue(queueName)
      .getOrElse(throw new QueueDoesNotExistException(queueName))

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

      messageStorage(queueName).persistMessage(message)
      
      logger.debug("Sent message: %s to %s".format(message.id, queueName))
      
      new NativeMessage(queueName, message)
    }

    def receiveMessage(visibilityTimeout: VisibilityTimeout) = {
      val messageOption = doReceiveMessage(visibilityTimeout)

      messageOption.foreach(message => volatileTaskScheduler.schedule {
        val statsStorage = messageStatisticsStorage(queueName)
        try {
          val stats = statsStorage.readMessageStatistics(message.id)
          val bumpedStats = bumpMessageStatistics(stats)
          statsStorage.writeMessageStatistics(message.id, bumpedStats)
        } catch {
          // This may happen if the message is deleted before the stats are bumped.
          case _: MessageDoesNotExistException => // ignore
        }
        
        logger.debug("Received message: %s with visibility timeout: %s".format(message.id, visibilityTimeout))
      })

      messageOption.map(new NativeMessage(queueName, _))
    }

    def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout) = {
      val messageOption = doReceiveMessage(visibilityTimeout)

      messageOption.map(message => {
        val statsStorage = messageStatisticsStorage(queueName)
        val stats = statsStorage.readMessageStatistics(message.id)
        val bumpedStats = bumpMessageStatistics(stats)
        volatileTaskScheduler.schedule {
          statsStorage.writeMessageStatistics(message.id, bumpedStats)
        }

        logger.debug("Received message: %s with statistics and visibility timeout: %s"
          .format(message.id, visibilityTimeout))
        
        (new NativeMessage(queueName, message), bumpedStats)
      })
    }

    private def doReceiveMessage(visibilityTimeout: VisibilityTimeout) = {
      val newNextDelivery = visibilityTimeout match {
        case DefaultVisibilityTimeout => defaultNextDelivery()
        case MillisVisibilityTimeout(millis) => computeNextDelivery(millis)
      }

      messageStorage(queueName).receiveMessage(now, newNextDelivery)
    }

    def lookupMessage(messageId: MessageId) = {
      messageStorage(queueName).lookupMessage(messageId).map(new NativeMessage(queueName, _))
    }

    def updateDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout): Queue = {
      queueStorage.updateQueue(data.copy(defaultVisibilityTimeout = defaultVisibilityTimeout))
      logger.debug("Updated visibility timeout of queue: %s to: %s".format(queueName, defaultVisibilityTimeout))
      fetchQueue()
    }

    def updateDelay(delay: Duration): Queue = {
      queueStorage.updateQueue(data.copy(delay = delay))
      logger.debug("Updated delay of queue: %s to: %s".format(queueName, delay))
      fetchQueue()
    }

    def fetchStatistics() = queueStorage.queueStatistics(queueName, now)

    def delete() {
      queueStorage.deleteQueue(queueName)
      logger.debug("Deleted queue: %s".format(queueName))
    }

    def fetchQueue() = {
      clearData()
      this
    }

    def messageOperations(id: MessageId) = new NativeMessage(queueName, id)

    // Queue

    def name = queueName

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