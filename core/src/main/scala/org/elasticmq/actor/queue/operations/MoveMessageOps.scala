package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage, QueueEvent}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{DeduplicationId, MoveDestination, MoveToDLQ}

trait MoveMessageOps extends Logging {
  this: QueueActorStorage =>

  def moveMessage(message: InternalMessage, destination: MoveDestination): ResultWithEvents[Unit] = {

    copyMessagesToActorRef.foreach { _ ! SendMessage(message.toNewMessageData) }

    destination match {
      case MoveToDLQ =>
        if (queueData.isFifo) {
          CommonOperations.wasRegistered(message.toNewMessageData, fifoMessagesHistory) match {
            case Some(_) => ResultWithEvents.empty
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

  private def moveMessageToQueue(internalMessage: InternalMessage): ResultWithEvents[Unit] = {
    messageQueue += internalMessage
    fifoMessagesHistory = fifoMessagesHistory.addNew(internalMessage)

    ResultWithEvents.onlyEvents(List(QueueEvent.MessageAdded(queueData.name, internalMessage)))
  }
}
