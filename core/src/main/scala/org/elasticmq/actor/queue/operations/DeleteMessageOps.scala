package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent}
import org.elasticmq.util.Logging
import org.elasticmq.{DeliveryReceipt, InvalidReceiptHandle}

trait DeleteMessageOps extends Logging {
  this: QueueActorStorage =>

  def deleteMessage(deliveryReceipt: DeliveryReceipt): ResultWithEvents[Either[InvalidReceiptHandle, Unit]] = {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.getById(msgId) match {
      case Some(msgData) =>
        if (msgData.deliveryReceipts.lastOption.contains(deliveryReceipt.receipt)) {
          // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
          messageQueue.remove(msgId)
          ResultWithEvents.valueWithEvents(Right(()), List(QueueEvent.MessageRemoved(queueData.name, msgId)))
        } else {
          ResultWithEvents.onlyValue(Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt)))
        }
      case None =>
        ResultWithEvents.onlyValue(Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt)))
    }
  }
}
