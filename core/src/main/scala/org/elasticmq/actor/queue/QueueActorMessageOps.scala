package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure.{Expired, Invalid}
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{DeleteMessage, LookupMessage, ReceiveMessages, SendMessage, UpdateVisibilityTimeout, _}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{MessageData, MessageId, MillisNextDelivery, NewMessageData, _}

trait QueueActorMessageOps extends Logging {
  this: QueueActorStorage =>

  def nowProvider: NowProvider
  def context: akka.actor.ActorContext

  def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = msg match {
    case SendMessage(message)                                  => handleOrRedirectMessage(message)
    case UpdateVisibilityTimeout(messageId, visibilityTimeout) => updateVisibilityTimeout(messageId, visibilityTimeout)
    case ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId) =>
      receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
    case DeleteMessage(deliveryReceipt) => deleteMessage(deliveryReceipt)
    case LookupMessage(messageId)       => messageQueue.byId.get(messageId.id).map(_.toMessageData)
  }

  private def handleOrRedirectMessage(message: NewMessageData): ReplyAction[MessageData] = {
    copyMessagesToActorRef.foreach { _ ! SendMessage(message) }

    moveMessagesToActorRef match {
      case Some(moveTo) =>
        // preserve original sender so that reply would be received there from the move-to actor
        implicit val sender: ActorRef = context.sender()
        moveTo ! SendMessage(message)
        DoNotReply()

      case None =>
        sendMessage(message)
    }
  }

  private def sendMessage(message: NewMessageData): MessageData = {
    if (queueData.isFifo) {
      // Ensure a message with the same deduplication id is not on the queue already. If the message is already on the
      // queue, return that -- don't add it twice
      // TODO: A message dedup id should be checked up to 5 mins after it has been received. If it has been deleted
      // during that period, it should _still_ be used when deduplicating new messages. If there's a match with a
      // deleted message (that was sent less than 5 minutes ago, the new message should not be added).
      messageQueue.byId.values.find(isDuplicate(message, _)) match {
        case Some(messageOnQueue) => messageOnQueue.toMessageData
        case None                 => addMessage(message)
      }
    } else {
      addMessage(message)
    }
  }

  /**
    * Check whether a new message is a duplicate of the message that's on the queue.
    *
    * @param newMessage      The message that needs to be added to the queue
    * @param queueMessage    The message that's already on the queue
    * @return                Whether the new message counts as a duplicate
    */
  private def isDuplicate(newMessage: NewMessageData, queueMessage: InternalMessage): Boolean = {
    lazy val isWithinDeduplicationWindow = queueMessage.created.plusMinutes(5).isAfter(nowProvider.now)
    newMessage.messageDeduplicationId == queueMessage.messageDeduplicationId && isWithinDeduplicationWindow
  }

  private def addMessage(message: NewMessageData) = {
    val internalMessage = InternalMessage.from(message, queueData)
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

  protected def receiveMessages(
      visibilityTimeout: VisibilityTimeout,
      count: Int,
      receiveRequestAttemptId: Option[String]
  ): List[MessageData] = {
    implicit val np = nowProvider
    val messages = receiveRequestAttemptId
      .flatMap({ attemptId =>
        // for a given request id, check for any messages we've dequeued and cached
        val cachedMessages = getMessagesFromRequestAttemptCache(attemptId)

        // if the cache returns an empty list instead of None, we still want to pull messages from
        // from the queue so return None in that case to properly process down stream
        cachedMessages.getOrElse(Nil) match {
          case Nil     => None
          case default => Some(default)
        }
      })
      .getOrElse(getMessagesFromQueue(visibilityTimeout, count))
      .map { internalMessage =>
        // Putting the msg again into the queue, with a new next delivery
        val newNextDelivery = computeNextDelivery(visibilityTimeout)
        internalMessage.trackDelivery(newNextDelivery)
        messageQueue += internalMessage

        logger.debug(s"${queueData.name}: Receiving message ${internalMessage.id}")
        internalMessage
      }

    receiveRequestAttemptId.foreach { attemptId =>
      receiveRequestAttemptCache.add(attemptId, messages)
    }

    messages.map(_.toMessageData)
  }

  private def getMessagesFromRequestAttemptCache(
      receiveRequestAttemptId: String
  )(implicit np: NowProvider): Option[List[InternalMessage]] = {
    receiveRequestAttemptCache.get(receiveRequestAttemptId, messageQueue) match {
      case Left(Expired)         => throw new RuntimeException("Attempt expired")
      case Left(Invalid)         => throw new RuntimeException("Invalid")
      case Right(None)           => None
      case Right(Some(messages)) => Some(messages)
    }
  }

  private def getMessagesFromQueue(visibilityTimeout: VisibilityTimeout, count: Int) = {
    val deliveryTime = nowProvider.nowMillis
    messageQueue.dequeue(count, deliveryTime).flatMap { internalMessage =>
      if (queueData.deadLettersQueue.map(_.maxReceiveCount).exists(_ <= internalMessage.receiveCount)) {
        logger.debug(s"${queueData.name}: send message $internalMessage to dead letters actor $deadLettersActorRef")
        deadLettersActorRef.foreach(_ ! SendMessage(internalMessage.toNewMessageData))
        internalMessage.deliveryReceipts.foreach(dr => deleteMessage(DeliveryReceipt(dr)))
        None
      } else {
        Some(internalMessage)
      }
    }
  }

  private def computeNextDelivery(visibilityTimeout: VisibilityTimeout) = {
    val nextDeliveryDelta = getVisibilityTimeoutMillis(visibilityTimeout)
    MillisNextDelivery(nowProvider.nowMillis + nextDeliveryDelta)
  }

  private def getVisibilityTimeoutMillis(visibilityTimeout: VisibilityTimeout) = visibilityTimeout match {
    case DefaultVisibilityTimeout        => queueData.defaultVisibilityTimeout.millis
    case MillisVisibilityTimeout(millis) => millis
  }

  private def deleteMessage(deliveryReceipt: DeliveryReceipt): Unit = {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.byId.get(msgId).foreach { msgData =>
      if (msgData.deliveryReceipts.lastOption.contains(deliveryReceipt.receipt)) {
        // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
        messageQueue.remove(msgId)
      }
    }
  }
}
