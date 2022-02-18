package org.elasticmq.actor.queue.operations

import org.elasticmq._
import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent}
import org.elasticmq.util.Logging

trait UpdateVisibilityTimeoutOps extends Logging {
  this: QueueActorStorage =>

  def updateVisibilityTimeout(
      deliveryReceipt: DeliveryReceipt,
      visibilityTimeout: VisibilityTimeout
  ): ResultWithEvents[Either[InvalidReceiptHandle, Unit]] = {
    updateNextDelivery(deliveryReceipt, CommonOperations.computeNextDelivery(visibilityTimeout, queueData, nowProvider))
  }

  private def updateNextDelivery(
      deliveryReceipt: DeliveryReceipt,
      newNextDelivery: MillisNextDelivery
  ): ResultWithEvents[Either[InvalidReceiptHandle, Unit]] = {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.byId.get(msgId) match {
      case Some(msg) if msg.deliveryReceipts.lastOption.contains(deliveryReceipt.receipt) =>
        // Updating
        val oldNextDelivery = msg.nextDelivery
        msg.nextDelivery = newNextDelivery.millis

        if (newNextDelivery.millis < oldNextDelivery) {
          // We have to re-insert the msg, as another msg with a bigger next delivery may be now before it,
          // so the msg wouldn't be correctly received.
          // (!) This may be slow (!)
          messageQueue = messageQueue.filterNot(_.id == msg.id)
          messageQueue += msg
        }
        // Else:
        // Just increasing the next delivery. Common case. It is enough to increase the value in the object. No need to
        // re-insert the msg into the queue, as it will be reinserted if needed during receiving.

        logger.debug(s"${queueData.name}: Updated next delivery of $msgId to $newNextDelivery")

        ResultWithEvents.valueWithEvents(
          Right(()),
          List(QueueEvent.MessageUpdated(queueData.name, msg))
        )
      case _ =>
        ResultWithEvents.onlyValue(Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt)))
    }
  }

}
