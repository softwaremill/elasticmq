package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging

trait MoveMessageOps extends Logging {
  this: QueueActorStorage =>

  def moveMessage(message: InternalMessage): Unit = {

    copyMessagesToActorRef.foreach { _ ! SendMessage(message.toNewMessageData) }

    if (queueData.isFifo) {
      // Ensure a message with the same deduplication id is not on the queue already. If the message is already on the
      // queue do nothing.
      // TODO: A message dedup id should be checked up to 5 mins after it has been received. If it has been deleted
      // during that period, it should _still_ be used when deduplicating new messages. If there's a match with a
      // deleted message (that was sent less than 5 minutes ago, the new message should not be added).
      messageQueue.byId.values.find(CommonOperations.isDuplicate(message.toNewMessageData, _, nowProvider)) match {
        case Some(_) => ()
        case None    => moveMessageToQueue(message)
      }
    } else {
      moveMessageToQueue(message)
    }
  }

  private def moveMessageToQueue(internalMessage: InternalMessage): Unit = {
    messageQueue += internalMessage
    logger.debug(s"Moved message with id ${internalMessage.id} to ${queueData.name}")
  }
}
