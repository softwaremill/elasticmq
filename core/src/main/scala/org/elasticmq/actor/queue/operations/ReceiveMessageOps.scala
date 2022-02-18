package org.elasticmq.actor.queue.operations

import org.elasticmq._
import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure.{Expired, Invalid}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage, QueueEvent}
import org.elasticmq.msg.MoveMessage
import org.elasticmq.util.{Logging, NowProvider}

trait ReceiveMessageOps extends Logging {
  this: QueueActorStorage with DeleteMessageOps =>

  trait MessageWithDestination
  case class MessageToReturn(internalMessage: InternalMessage) extends MessageWithDestination
  case class MessageToDelete(internalMessage: InternalMessage) extends MessageWithDestination

  protected def receiveMessages(
      visibilityTimeout: VisibilityTimeout,
      count: Int,
      receiveRequestAttemptId: Option[String]
  ): ResultWithEvents[List[MessageData]] = {
    implicit val np: NowProvider = nowProvider

    val messagesWithDestinations = receiveRequestAttemptId
      .flatMap({ attemptId => getCachedMessages(attemptId) })
      .getOrElse(getMessagesFromQueue(count))

    val messagesToReturn = messagesWithDestinations.flatMap {
      case MessageToReturn(internalMessage) => Some(internalMessage)
      case _                                => None
    } map { internalMessage =>
      // Putting the msg again into the queue, with a new next delivery
      val newNextDelivery = CommonOperations.computeNextDelivery(visibilityTimeout, queueData, np)
      internalMessage.trackDelivery(newNextDelivery)
      messageQueue += internalMessage

      logger.debug(s"${queueData.name}: Receiving message ${internalMessage.id}")
      internalMessage
    }

    val updateEvents =
      messagesToReturn.map(internalMessage => QueueEvent.MessageUpdated(queueData.name, internalMessage))

    val deleteEvents = messagesWithDestinations.flatMap {
      case MessageToDelete(internalMessage) => Some(internalMessage)
      case _                                => None
    } flatMap { internalMessage =>
      internalMessage.deliveryReceipts.toList.map(dr => deleteMessage(DeliveryReceipt(dr))).flatMap(_.events)
    }

    receiveRequestAttemptId.foreach { attemptId => receiveRequestAttemptCache.add(attemptId, messagesToReturn) }

    ResultWithEvents.valueWithEvents(
      messagesToReturn.map(_.toMessageData),
      updateEvents ++ deleteEvents
    )
  }

  private def getCachedMessages(
      attemptId: String
  )(implicit np: NowProvider): Option[List[MessageWithDestination]] = {
    // for a given request id, check for any messages we've dequeued and cached
    val cachedMessages = getMessagesFromRequestAttemptCache(attemptId)

    // if the cache returns an empty list instead of None, we still want to pull messages from
    // from the queue so return None in that case to properly process down stream
    cachedMessages.getOrElse(Nil) match {
      case Nil     => None
      case default => Some(default.map(internalMessage => MessageToReturn(internalMessage)))
    }
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

  private def getMessagesFromQueue(count: Int): List[MessageWithDestination] = {
    val deliveryTime = nowProvider.nowMillis
    messageQueue.dequeue(count, deliveryTime).map { internalMessage =>
      if (queueData.deadLettersQueue.map(_.maxReceiveCount).exists(_ <= internalMessage.receiveCount)) {
        logger.debug(s"${queueData.name}: send message $internalMessage to dead letters actor $deadLettersActorRef")
        deadLettersActorRef.foreach(_ ! MoveMessage(internalMessage, MoveToDLQ))
        MessageToDelete(internalMessage)
      } else {
        MessageToReturn(internalMessage)
      }
    }
  }
}
