package org.elasticmq.actor.test

import org.elasticmq._
import org.joda.time.{DateTime, Duration}
import org.elasticmq.MessageId
import org.elasticmq.data.NewMessageData
import org.elasticmq.data.MessageData
import org.elasticmq.MillisNextDelivery
import org.elasticmq.data.QueueData

trait DataCreationHelpers {
  def createQueueData(name: String, defaultVisibilityTimeout: MillisVisibilityTimeout) =
    QueueData(name, defaultVisibilityTimeout, Duration.ZERO, new DateTime(0), new DateTime(0))

  def createMessageData(id: String, content: String, nextDelivery: MillisNextDelivery,
                        deliveryReceipt: Option[DeliveryReceipt] = None) =
    MessageData(MessageId(id), deliveryReceipt, content, nextDelivery, new DateTime(0), MessageStatistics(NeverReceived, 0))

  def createNewMessageData(id: String, content: String, nextDelivery: MillisNextDelivery) =
    NewMessageData(Some(MessageId(id)), content, nextDelivery)

  def createNewMessageData(messageData: MessageData) =
    NewMessageData(Some(messageData.id), messageData.content, messageData.nextDelivery)
}