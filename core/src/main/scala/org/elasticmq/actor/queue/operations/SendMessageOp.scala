package org.elasticmq.actor.queue.operations

import akka.actor.{ActorContext, ActorRef}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.actor.reply.{DoNotReply, ReplyAction}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{MessageData, NewMessageData}

trait SendMessageOp extends Logging {
  this: QueueActorStorage =>

  def handleOrRedirectMessage(message: NewMessageData, context: ActorContext): ReplyAction[MessageData] = {
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
      messageQueue.byId.values.find(CommonOperations.isDuplicate(message, _, nowProvider)) match {
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
    logger.debug(s"${queueData.name}: Sent message with id ${internalMessage.id}")

    internalMessage.toMessageData
  }
}
