package org.elasticmq.actor

import org.elasticmq._
import org.elasticmq.msg._
import com.typesafe.scalalogging.slf4j.Logging

trait QueueActorQueueOps extends Logging {
  this: QueueActorStorage =>

  def receiveAndReplyQueueMsg[T](msg: QueueQueueMsg[T]): T = msg match {
    case GetQueueData() => queueData
    case UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout) => {
      logger.info(s"Updating default visibility timeout of ${queueData.name} to $newDefaultVisibilityTimeout")
      queueData = queueData.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
    }
    case UpdateQueueDelay(newDelay) => {
      logger.info(s"Updating delay of ${queueData.name} to $newDelay")
      queueData = queueData.copy(delay = newDelay)
    }

    case GetQueueStatistics(deliveryTime) => getQueueStatistics(deliveryTime)
  }

  def getQueueStatistics(deliveryTime: Long) = {
    var visible = 0
    var invisible = 0
    var delayed = 0

    messageQueue.foreach { internalMessage =>
      if (internalMessage.nextDelivery < deliveryTime) {
        visible += 1
      } else if (internalMessage.deliveryReceipt.isDefined) {
        invisible +=1
      } else {
        delayed += 1
      }
    }

    QueueStatistics(visible, invisible, delayed)
  }
}
