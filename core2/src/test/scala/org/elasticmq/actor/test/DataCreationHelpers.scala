package org.elasticmq.actor.test

import org.elasticmq.data.{NewMessageData, MessageData, QueueData}
import org.elasticmq.{DeliveryReceipt, MessageId, MillisNextDelivery, MillisVisibilityTimeout}
import org.joda.time.{DateTime, Duration}

trait DataCreationHelpers {
  def createQueueData(name: String, defaultVisibilityTimeout: MillisVisibilityTimeout) =
    QueueData(name, defaultVisibilityTimeout, Duration.ZERO, new DateTime(0), new DateTime(0))

  def createMessageData(id: String, content: String, nextDelivery: MillisNextDelivery,
                        deliveryReceipt: Option[DeliveryReceipt] = None) =
    MessageData(MessageId(id), deliveryReceipt, content, nextDelivery, new DateTime(0))

  def createNewMessageData(id: String, content: String, nextDelivery: MillisNextDelivery) =
    NewMessageData(MessageId(id), content, nextDelivery, new DateTime(0))

  def createNewMessageData(messageData: MessageData) =
    NewMessageData(messageData.id, messageData.content, messageData.nextDelivery, messageData.created)
}