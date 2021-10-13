package org.elasticmq.actor.queue.operations

import org.elasticmq.actor.queue.QueueActorStorage
import org.elasticmq.util.Logging
import org.elasticmq.{DeliveryReceipt, InvalidReceiptHandle}

import scala.concurrent.Future

trait DeleteMessageOps extends Logging {
  this: QueueActorStorage =>

  def deleteMessage(deliveryReceipt: DeliveryReceipt): Future[Either[InvalidReceiptHandle, Unit]] = {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.byId.get(msgId) match {
      case Some(msgData) =>
        if (msgData.deliveryReceipts.lastOption.contains(deliveryReceipt.receipt)) {
          // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
          messageQueue.remove(msgId)
          sendMessageRemovedNotification(msgId).map(_ => Right(()))
        } else {
          Future.successful(Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt)))
        }
      case None =>
        Future.successful(Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt)))
    }
  }
}
