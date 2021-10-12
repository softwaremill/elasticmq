package org.elasticmq.actor.queue.operations

import akka.pattern.ask
import akka.util.Timeout
import org.elasticmq.actor.queue.{QueueActorStorage, QueueMessageRemoved}
import org.elasticmq.util.Logging
import org.elasticmq.{DeliveryReceipt, InvalidReceiptHandle}

import scala.concurrent.{Await, ExecutionContext}

trait DeleteMessageOps extends Logging {
  this: QueueActorStorage =>

  def deleteMessage(deliveryReceipt: DeliveryReceipt): Either[InvalidReceiptHandle, Unit] = {
    val msgId = deliveryReceipt.extractId.toString

    messageQueue.byId.get(msgId) match {
      case Some(msgData) =>
        if (msgData.deliveryReceipts.lastOption.contains(deliveryReceipt.receipt)) {
          // Just removing the msg from the map. The msg will be removed from the queue when trying to receive it.
          messageQueue.remove(msgId)
          sendMessageRemovedNotification(msgId)
          Right(())
        } else {
          Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt))
        }
      case None =>
        Left(new InvalidReceiptHandle(queueData.name, deliveryReceipt.receipt))
    }
  }
}
