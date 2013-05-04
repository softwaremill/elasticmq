package org.elasticmq.actor

import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.message._
import scala.reflect._
import org.elasticmq.message.GetQueueStatistics
import org.elasticmq.message.UpdateQueueDefaultVisibilityTimeout
import org.elasticmq.data.QueueData

class QueueActor(initialQueueData: QueueData) extends ReplyingActor with Logging {
  type M[X] = QueueMessage[X]
  val ev = classTag[M[Unit]]

  private var queueData = initialQueueData

  def receiveAndReply[T](msg: QueueMessage[T]) = msg match {
    case GetQueueData() => queueData
    case UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout) => {
      logger.info(s"Updating default visibility timeout of ${queueData.name} to $newDefaultVisibilityTimeout")
      queueData = queueData.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
    }
    case UpdateQueueDelay(newDelay) => {
      logger.info(s"Updating delay of ${queueData.name} to $newDelay")
      queueData = queueData.copy(delay = newDelay)
    }

    case SendMessage(message) => ()
    case UpdateNextDelivery(messageId, newNextDelivery) => ()
    case ReceiveMessage(deliveryTime, newNextDelivery) => None
    case DeleteMessage(messageId) =>
    case LookupMessage(messageId) => None

    case GetQueueStatistics(deliveryTime) => null
    case GetMessageStatistics(messageId) => null
  }
}
