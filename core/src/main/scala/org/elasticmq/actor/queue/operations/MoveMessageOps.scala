package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{DeduplicationId, MoveDestination, MoveToDLQ}

trait MoveMessageOps extends Logging {
  this: QueueActorStorage =>

  def moveMessage(message: InternalMessage, destination: MoveDestination): Unit = {

    copyMessagesToActorRef.foreach { _ ! SendMessage(message.toNewMessageData) }

    destination match {
      case MoveToDLQ =>
        if (queueData.isFifo) {
          CommonOperations.wasRegistered(message.toNewMessageData, fifoMessagesHistory) match {
            case Some(_) => ()
            case None =>
              logger.debug(s"Moved message (${message.id}) from FIFO queue to ${queueData.name}")
              moveMessageToQueue(regenerateDeduplicationId(message))
          }
        } else {
          logger.debug(s"Moved message (${message.id}) to ${queueData.name}")
          moveMessageToQueue(message)
        }
    }
  }

  private def regenerateDeduplicationId(internalMessage: InternalMessage): InternalMessage = {
    internalMessage.copy(
      messageDeduplicationId = Some(DeduplicationId(internalMessage.id))
    )
  }

  private def moveMessageToQueue(internalMessage: InternalMessage): Unit = {
    messageQueue += internalMessage.copy(persistedId = None)
    fifoMessagesHistory = fifoMessagesHistory.addNew(internalMessage)
  }
}
