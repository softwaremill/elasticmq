package org.elasticmq.actor.queue

import org.elasticmq.{FifoDeduplicationIdsHistory, QueueStatistics}
import org.elasticmq.actor.reply.{valueToReplyWith, ReplyAction}
import org.elasticmq.msg._
import org.elasticmq.util.Logging

trait QueueActorQueueOps extends Logging {
  this: QueueActorStorage =>

  def receiveAndReplyQueueMsg[T](msg: QueueQueueMsg[T]): ReplyAction[T] =
    msg match {
      case GetQueueData() => queueData
      case UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout) =>
        logger.info(s"${queueData.name}: Updating default visibility timeout to $newDefaultVisibilityTimeout")
        queueData = queueData.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
      case UpdateQueueDelay(newDelay) =>
        logger.info(s"${queueData.name}: Updating delay to $newDelay")
        queueData = queueData.copy(delay = newDelay)
      case UpdateQueueReceiveMessageWait(newReceiveMessageWait) =>
        logger.info(s"${queueData.name}: Updating receive message wait to $newReceiveMessageWait")
        queueData = queueData.copy(receiveMessageWait = newReceiveMessageWait)
      case UpdateQueueDeadLettersQueue(newDeadLettersQueue, newDeadLettersQueueActor) =>
        if (newDeadLettersQueue.isDefined && newDeadLettersQueueActor.isDefined) {
          logger.info(
            s"${queueData.name}: Updating Dead Letters Queue to ${newDeadLettersQueue.get.name} with maxReceiveCount ${newDeadLettersQueue.get.maxReceiveCount}"
          )
          deadLettersActorRef = newDeadLettersQueueActor
          queueData = queueData.copy(deadLettersQueue = newDeadLettersQueue)
        } else {
          if (newDeadLettersQueue.isDefined || newDeadLettersQueueActor.isDefined) {
            logger.warn("Removing DLQ as both settings and actor must be given.")
          }
          logger.info(s"${queueData.name}: Removing Dead Letters Queue")
          deadLettersActorRef = None
          queueData = queueData.copy(deadLettersQueue = None)
        }
      case ClearQueue() =>
        logger.info(s"${queueData.name}: Clearing queue")
        messageQueue.clear()
        fifoMessagesHistory = FifoDeduplicationIdsHistory.newHistory()
      case GetQueueStatistics(deliveryTime) => getQueueStatistics(deliveryTime)
      case UpdateQueueTags(newQueueTags) =>
        logger.info(s"${queueData.name} Adding and Updating tags ${newQueueTags}")
        queueData = queueData.copy(tags = queueData.tags ++ newQueueTags)
      case RemoveQueueTags(tagsToRemove) =>
        logger.info(s"${queueData.name} Removing tags")
        var tmpTags = queueData.tags
        for (tag <- tagsToRemove) {
          tmpTags -= tag
        }
        queueData = queueData.copy(tags = tmpTags)
    }

  private def getQueueStatistics(deliveryTime: Long) = {
    var visible = 0L
    var invisible = 0L
    var delayed = 0L

    messageQueue.all.foreach { internalMessage =>
      if (internalMessage.nextDelivery < deliveryTime) {
        visible += 1
      } else if (internalMessage.deliveryReceipts.nonEmpty) {
        invisible += 1
      } else {
        delayed += 1
      }
    }

    QueueStatistics(visible, invisible, delayed)
  }
}
