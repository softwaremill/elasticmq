package org.elasticmq.actor.queue.operations

import akka.actor.{ActorContext, ActorRef}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{MessageData, NewMessageData}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.Future

trait SendMessageOp extends Logging {
  this: QueueActorStorage =>

  def handleOrRedirectMessage(message: NewMessageData, context: ActorContext): Unit = {
    val message2 = if (queueData.isFifo && message.sequenceNumber.isEmpty) {
      message.copy(sequenceNumber = Some(SequenceNumber.next()))
    } else message

    copyMessagesToActorRef.foreach { _ ! SendMessage(message2) }

    moveMessagesToActorRef match {
      case Some(moveTo) =>
        implicit val sender: ActorRef = context.sender()
        // preserve original sender so that reply would be received there from the move-to actor
        moveTo ! SendMessage(message2)

      case None =>
        val sender = context.sender()
        sendMessage(message2).map(msg => sender ! msg)
    }
  }

  def restoreMessages(messages: List[InternalMessage]): Unit = {
    messages.foreach(addInternalMessage)
    logger.info(s"Restored ${messages.size} messages")
  }

  private def sendMessage(message: NewMessageData): Future[MessageData] = {
    if (queueData.isFifo) {
      CommonOperations.wasRegistered(message, fifoMessagesHistory) match {
        case Some(messageOnQueue) => Future.successful(messageOnQueue.toMessageData)
        case None                 => addMessage(message)
      }
    } else {
      addMessage(message)
    }
  }

  private def addMessage(message: NewMessageData): Future[MessageData] = {
    val internalMessage = InternalMessage.from(message, queueData)
    addInternalMessage(internalMessage)
    logger.debug(s"${queueData.name}: Sent message with id ${internalMessage.id}")

    sendMessageAddedNotification(internalMessage)
      .map(_ => internalMessage.toMessageData)
  }

  private def addInternalMessage(internalMessage: InternalMessage) = {
    messageQueue += internalMessage
    fifoMessagesHistory = fifoMessagesHistory.addNew(internalMessage)
  }
}

private object SequenceNumber {
  private val current = new AtomicLong

  def next(): String = current.getAndIncrement().toString
}
