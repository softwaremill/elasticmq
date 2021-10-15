package org.elasticmq.actor.queue.operations

import org.elasticmq._
import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure.{Expired, Invalid}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage, QueueMessageUpdated}
import org.elasticmq.msg.MoveMessage
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.Future

trait ReceiveMessageOps extends Logging {
  this: QueueActorStorage with DeleteMessageOps =>

  protected def receiveMessages(
      visibilityTimeout: VisibilityTimeout,
      count: Int,
      receiveRequestAttemptId: Option[String]
  ): ResultWithEvents[List[MessageData]] = {
    implicit val np: NowProvider = nowProvider
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
      .getOrElse(getMessagesFromQueue(count))
      .map { internalMessage =>
        // Putting the msg again into the queue, with a new next delivery
        val newNextDelivery = CommonOperations.computeNextDelivery(visibilityTimeout, queueData, np)
        internalMessage.trackDelivery(newNextDelivery)
        messageQueue += internalMessage

        logger.debug(s"${queueData.name}: Receiving message ${internalMessage.id}")
        internalMessage
      }

    receiveRequestAttemptId.foreach { attemptId => receiveRequestAttemptCache.add(attemptId, messages) }

    ResultWithEvents.some(
      messages.map(_.toMessageData),
      messages.map(internalMessage => QueueMessageUpdated(queueData.name, internalMessage))
    )
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

  private def getMessagesFromQueue(count: Int) = {
    val deliveryTime = nowProvider.nowMillis
    messageQueue.dequeue(count, deliveryTime).flatMap { internalMessage =>
      if (queueData.deadLettersQueue.map(_.maxReceiveCount).exists(_ <= internalMessage.receiveCount)) {
        logger.debug(s"${queueData.name}: send message $internalMessage to dead letters actor $deadLettersActorRef")
        deadLettersActorRef.foreach(_ ! MoveMessage(internalMessage, MoveToDLQ))
        internalMessage.deliveryReceipts.foreach(dr => deleteMessage(DeliveryReceipt(dr)))
        None
      } else {
        Some(internalMessage)
      }
    }
  }
}
