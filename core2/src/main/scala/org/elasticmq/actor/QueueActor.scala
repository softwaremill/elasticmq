package org.elasticmq.actor

import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.msg._
import scala.reflect._
import org.elasticmq.data.MessageDoesNotExist
import scala.collection.mutable
import org.elasticmq._
import org.joda.time.DateTime
import scala.annotation.tailrec
import org.elasticmq.util.NowProvider
import org.elasticmq.msg.GetQueueStatistics
import org.elasticmq.msg.GetQueueData
import org.elasticmq.msg.UpdateNextDelivery
import org.elasticmq.msg.UpdateQueueDefaultVisibilityTimeout
import org.elasticmq.data.QueueData
import org.elasticmq.data.NewMessageData
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.msg.UpdateQueueDelay
import org.elasticmq.MessageId
import org.elasticmq.msg.SendMessage
import org.elasticmq.data.MessageData
import org.elasticmq.MillisNextDelivery
import org.elasticmq.msg.ReceiveMessage
import org.elasticmq.msg.LookupMessage

class QueueActor(nowProvider: NowProvider, initialQueueData: QueueData) extends ReplyingActor with Logging {
  type M[X] = QueueMsg[X]
  val ev = classTag[M[Unit]]

  private var queueData = initialQueueData
  private var messageQueue = mutable.PriorityQueue[InternalMessage]()
  private val messagesById = mutable.HashMap[String, InternalMessage]()

  def receiveAndReply[T](msg: QueueMsg[T]) = msg match {
    case GetQueueData() => queueData
    case UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout) => {
      logger.info(s"Updating default visibility timeout of ${queueData.name} to $newDefaultVisibilityTimeout")
      queueData = queueData.copy(defaultVisibilityTimeout = newDefaultVisibilityTimeout)
    }
    case UpdateQueueDelay(newDelay) => {
      logger.info(s"Updating delay of ${queueData.name} to $newDelay")
      queueData = queueData.copy(delay = newDelay)
    }

    case SendMessage(message) => sendMessage(message)
    case UpdateNextDelivery(messageId, newNextDelivery) => updateNextDelivery(messageId, newNextDelivery)
    case ReceiveMessage(deliveryTime, newNextDelivery) => receiveMessage(deliveryTime, newNextDelivery)
    case DeleteMessage(messageId) => {
      // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
      messagesById.remove(messageId.id)
      ()
    }
    case LookupMessage(messageId) => messagesById.get(messageId.id).map(_.toMessageData)

    case GetQueueStatistics(deliveryTime) => getQueueStatistics(deliveryTime)
  }

  private def sendMessage(message: NewMessageData) {
    val internalMessage = InternalMessage.from(message)
    messageQueue += internalMessage
    messagesById(internalMessage.id) = internalMessage
  }

  private def updateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) = {
    messagesById.get(messageId.id) match {
      case Some(internalMessage) => {
        // Updating
        val oldNextDelivery = internalMessage.nextDelivery
        internalMessage.nextDelivery = newNextDelivery.millis

        if (newNextDelivery.millis < oldNextDelivery) {
          // We have to re-insert the msg, as another msg with a bigger next delivery may be now before it,
          // so the msg wouldn't be correctly received.
          // (!) This may be slow (!)
          messageQueue = messageQueue.filterNot(_.id == internalMessage.id)
          messageQueue += internalMessage
        }
        // Else:
        // Just increasing the next delivery. Common case. It is enough to increase the value in the object. No need to
        // re-insert the msg into the queue, as it will be reinserted if needed during receiving.

        Right(())
      }

      case None => Left(new MessageDoesNotExist(queueData.name, messageId))
    }
  }

  @tailrec
  private def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData] = {
    if (messageQueue.size == 0) {
      None
    } else {
      val internalMessage = messageQueue.dequeue()
      val id = MessageId(internalMessage.id)
      if (internalMessage.nextDelivery > deliveryTime) {
        // Putting the msg back. That's the youngest msg, so there is no msg that can be received.
        messageQueue += internalMessage
        None
      } else if (messagesById.contains(id.id)) {
        // Putting the msg again into the queue, with a new next delivery
        internalMessage.deliveryReceipt = Some(DeliveryReceipt.generate(id).receipt)
        internalMessage.nextDelivery = newNextDelivery.millis

        internalMessage.receiveCount += 1
        internalMessage.firstReceive = OnDateTimeReceived(new DateTime(deliveryTime))

        messageQueue += internalMessage

        Some(internalMessage.toMessageData)
      } else {
        // Deleted msg - trying again
        receiveMessage(deliveryTime, newNextDelivery)
      }
    }
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

  case class InternalMessage(id: String,
                             var deliveryReceipt: Option[String],
                             var nextDelivery: Long,
                             content: String,
                             created: DateTime,
                             var firstReceive: Received,
                             var receiveCount: Int)
    extends Comparable[InternalMessage] {

    // Priority queues have biggest elements first
    def compareTo(other: InternalMessage) = - nextDelivery.compareTo(other.nextDelivery)

    def toMessageData = MessageData(
      MessageId(id),
      deliveryReceipt.map(DeliveryReceipt(_)),
      content,
      MillisNextDelivery(nextDelivery),
      created,
      MessageStatistics(firstReceive, receiveCount))
  }

  object InternalMessage {
    def from(messageData: MessageData) = InternalMessage(
      messageData.id.id,
      messageData.deliveryReceipt.map(_.receipt),
      messageData.nextDelivery.millis,
      messageData.content,
      messageData.created,
      messageData.statistics.approximateFirstReceive,
      messageData.statistics.approximateReceiveCount)

    def from(newMessageData: NewMessageData) = InternalMessage(
      newMessageData.id.id,
      None,
      newMessageData.nextDelivery.toMillis(nowProvider.nowMillis).millis,
      newMessageData.content,
      nowProvider.now,
      NeverReceived,
      0)
  }
}
