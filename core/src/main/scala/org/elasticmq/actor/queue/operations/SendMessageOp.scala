package org.elasticmq.actor.queue.operations

import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.elasticmq.actor.queue.{InternalMessage, QueueActorStorage, QueueEvent}
import org.elasticmq.msg.SendMessage
import org.elasticmq.util.Logging
import org.elasticmq.{MessageData, NewMessageData}

import java.util.concurrent.atomic.AtomicLong

trait SendMessageOp extends Logging {
  this: QueueActorStorage =>

  def handleOrRedirectMessage(message: NewMessageData, context: ActorContext): ResultWithEvents[MessageData] = {
    val message2 = if (queueData.isFifo && message.sequenceNumber.isEmpty) {
      message.copy(sequenceNumber = Some(SequenceNumber.next()))
    } else message

    copyMessagesToActorRef.foreach { _ ! SendMessage(message2) }

    moveMessagesToActorRef match {
      case Some(moveTo) =>
        implicit val sender: ActorRef = context.sender()
        // preserve original sender so that reply would be received there from the move-to actor
        moveTo ! SendMessage(message2)
        ResultWithEvents.empty

      case None =>
        sendMessage(message2)
    }
  }

  def restoreMessages(messages: List[InternalMessage]): Unit = {
    messages.foreach(addInternalMessage)
    logger.info(s"Restored ${messages.size} messages in queue ${queueData.name}")
  }

  private def sendMessage(message: NewMessageData): ResultWithEvents[MessageData] = {
    if (queueData.isFifo) {
      CommonOperations.wasRegistered(message, fifoMessagesHistory) match {
        case Some(messageOnQueue) => ResultWithEvents.onlyValue(messageOnQueue.toMessageData)
        case None                 => addMessage(message)
      }
    } else {
      addMessage(message)
    }
  }

  private def addMessage(message: NewMessageData): ResultWithEvents[MessageData] = {
    val internalMessage = InternalMessage.from(message, queueData)
    addInternalMessage(internalMessage)
    logger.debug(s"${queueData.name}: Sent message with id ${internalMessage.id}")

    ResultWithEvents.valueWithEvents(
      internalMessage.toMessageData,
      List(QueueEvent.MessageAdded(queueData.name, internalMessage))
    )
  }

  private def addInternalMessage(internalMessage: InternalMessage): Unit = {
    messageQueue += internalMessage
    fifoMessagesHistory = fifoMessagesHistory.addNew(internalMessage)
  }
}

private object SequenceNumber {
  private val current = new AtomicLong

  def next(): String = current.getAndIncrement().toString
}
