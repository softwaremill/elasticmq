package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging

trait MoveMessageOps extends Logging {
  this: QueueActorStorage =>

  def moveMessage(message: InternalMessage): Unit = {

    copyMessagesToActorRef.foreach { _ ! SendMessage(message.toNewMessageData) }

    if (queueData.isFifo) {
      CommonOperations.wasRegistered(message.toNewMessageData, fifoMessagesHistory) match {
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
