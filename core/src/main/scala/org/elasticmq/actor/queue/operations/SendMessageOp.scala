package org.elasticmq.actor.queue.operations

import akka.actor.{ActorContext, ActorRef}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.actor.reply.{DoNotReply, ReplyAction}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{MessageData, NewMessageData}

import java.util.concurrent.atomic.AtomicLong

trait SendMessageOp extends Logging {
  this: QueueActorStorage =>

  def handleOrRedirectMessage(message: NewMessageData, context: ActorContext): ReplyAction[MessageData] = {
    val message2 = if (queueData.isFifo && message.sequenceNumber.isEmpty) {
      message.copy(sequenceNumber = Some(SequenceNumber.next()))
    } else message

    copyMessagesToActorRef.foreach { _ ! SendMessage(message2) }

    moveMessagesToActorRef match {
      case Some(moveTo) =>
        // preserve original sender so that reply would be received there from the move-to actor
        implicit val sender: ActorRef = context.sender()
        moveTo ! SendMessage(message2)
        DoNotReply()

      case None =>
        sendMessage(message2)
    }
  }

  private def sendMessage(message: NewMessageData): MessageData = {
    if (queueData.isFifo) {
      CommonOperations.wasRegistered(message, fifoMessagesHistory) match {
        case Some(messageOnQueue) => messageOnQueue.toMessageData
        case None                 => addMessage(message)
      }
    } else {
      addMessage(message)
    }
  }

  private def addMessage(message: NewMessageData) = {
    val internalMessage = InternalMessage.from(message, queueData)
    messageQueue += internalMessage
    fifoMessagesHistory = fifoMessagesHistory.addNew(internalMessage)
    logger.debug(s"${queueData.name}: Sent message with id ${internalMessage.id}")

    internalMessage.toMessageData
  }
}

private object SequenceNumber {
  private val current = new AtomicLong

  def next(): String = current.getAndIncrement().toString
}