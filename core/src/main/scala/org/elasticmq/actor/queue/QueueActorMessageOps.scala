package org.elasticmq.actor.queue

import org.elasticmq.actor.reply._
import org.elasticmq.msg.{DeleteMessage, LookupMessage, ReceiveMessages, SendMessage, UpdateVisibilityTimeout, _}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{MessageData, MessageId, MillisNextDelivery, NewMessageData, OnDateTimeReceived, _}
import org.joda.time.DateTime
import scala.annotation.tailrec

trait QueueActorMessageOps extends Logging {
  this: QueueActorStorage =>

  def nowProvider: NowProvider

  def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = msg match {
    case SendMessage(message) => sendMessage(message)
    case UpdateVisibilityTimeout(messageId, visibilityTimeout) => updateVisibilityTimeout(messageId, visibilityTimeout)
    case ReceiveMessages(visibilityTimeout, count, waitForMessages) => receiveMessages(visibilityTimeout, count)
    case DeleteMessage(deliveryReceipt) => deleteMessage(deliveryReceipt)
    case LookupMessage(messageId) => messageQueue.byId.get(messageId.id).map(_.toMessageData)
  }

  private def sendMessage(message: NewMessageData): MessageData = {
    if (queueData.isFifo) {
      // Ensure a message with the same deduplication id is not on the queue already. If the message is already on the
      // queue, return that -- don't add it twice
      // TODO: A message dedup id should be checked up to 5 mins after it has been deleted
      messageQueue.byId.values.find(_.messageDeduplicationId == message.messageDeduplicationId) match {
        case Some(messageOnQueue) => messageOnQueue.toMessageData
        case None => addMessage(message)
      }
    } else {
      addMessage(message)
    }
  }

  private def addMessage(message: NewMessageData) = {
    val internalMessage = InternalMessage.from(message)
    messageQueue += internalMessage
    logger.debug(s"${queueData.name}: Sent message with id ${internalMessage.id}")

    internalMessage.toMessageData
  }

  private def updateVisibilityTimeout(messageId: MessageId, visibilityTimeout: VisibilityTimeout) = {
    updateNextDelivery(messageId, computeNextDelivery(visibilityTimeout))
  }

  private def updateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) = {
    messageQueue.byId.get(messageId.id) match {
      case Some(internalMessage) =>
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

        logger.debug(s"${queueData.name}: Updated next delivery of $messageId to $newNextDelivery")

        Right(())

      case None => Left(new MessageDoesNotExist(queueData.name, messageId))
    }
  }

  protected def receiveMessages(visibilityTimeout: VisibilityTimeout, count: Int): List[MessageData] = {
    val deliveryTime = nowProvider.nowMillis

    @tailrec
    def doReceiveMessages(left: Int, acc: List[MessageData]): List[MessageData] = {
      if (left == 0) {
        acc
      } else {
        receiveMessage(deliveryTime, computeNextDelivery(visibilityTimeout), acc) match {
          case None => acc
          case Some(msg) => doReceiveMessages(left - 1, msg :: acc)
        }
      }
    }

    doReceiveMessages(count, List.empty)
  }

  private def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery, acc: List[MessageData]): Option[MessageData] = {
    if (messageQueue.isEmpty) {
      None
    } else {
      messageQueue.dequeue(acc).flatMap { internalMessage =>
        val id = MessageId(internalMessage.id)
        if (!internalMessage.deliverable(deliveryTime)) {
          // Putting the msg back. That's the youngest msg, so there is no msg that can be received.
          messageQueue += internalMessage
          None
        } else if (messageQueue.byId.contains(id.id)) {
          processInternalMessage(deliveryTime, newNextDelivery, internalMessage, acc)
        } else {
          // Deleted msg - trying again
          receiveMessage(deliveryTime, newNextDelivery, acc)
        }
      }
    }
  }

  private def processInternalMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery,
      internalMessage: InternalMessage, acc: List[MessageData]) = {
    // Putting the msg to dead letters queue if exists
    if (queueData.deadLettersQueue.map(_.maxReceiveCount).exists(_ <= internalMessage.receiveCount)) {
      logger.debug(s"${queueData.name}: send message $internalMessage to dead letters actor $deadLettersActorRef")
      deadLettersActorRef.foreach(_ ! SendMessage(internalMessage.toNewMessageData))
      internalMessage.deliveryReceipt.foreach(dr => deleteMessage(DeliveryReceipt(dr)))
      None
    } else if (messageQueue.isFifoBoundByOtherMessage(internalMessage, deliveryTime, acc)) {
      // Don't return the internal message if there's already one being returned for that message group. Similarly, if
      // a message within that message group is returned (but not acknowledged yet), don't return it either
      messageQueue += internalMessage
      None
    } else {
      // Putting the msg again into the queue, with a new next delivery
      internalMessage.deliveryReceipt = Some(DeliveryReceipt.generate(MessageId(internalMessage.id)).receipt)
      internalMessage.nextDelivery = newNextDelivery.millis

      internalMessage.receiveCount += 1
      internalMessage.firstReceive = OnDateTimeReceived(new DateTime(deliveryTime))

      messageQueue += internalMessage

      logger.debug(s"${queueData.name}: Receiving message ${internalMessage.id}")

      Some(internalMessage.toMessageData)
    }
  }

  private def computeNextDelivery(visibilityTimeout: VisibilityTimeout) = {
    val nextDeliveryDelta = getVisibilityTimeoutMillis(visibilityTimeout)
    MillisNextDelivery(nowProvider.nowMillis + nextDeliveryDelta)
  }

  private def getVisibilityTimeoutMillis(visibilityTimeout: VisibilityTimeout) = visibilityTimeout match {
    case DefaultVisibilityTimeout => queueData.defaultVisibilityTimeout.millis
    case MillisVisibilityTimeout(millis) => millis
  }

  private def deleteMessage(deliveryReceipt: DeliveryReceipt) {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.byId.get(msgId).foreach { msgData =>
      if (msgData.deliveryReceipt.contains(deliveryReceipt.receipt)) {
        // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
        messageQueue.remove(msgId)
      }
    }
  }
}
