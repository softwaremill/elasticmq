package org.elasticmq.actor.test

import org.elasticmq._
import org.joda.time.{DateTime, Duration}
import org.elasticmq.MessageId
import org.elasticmq.MillisNextDelivery

trait DataCreationHelpers {
  def createQueueData(
      name: String,
      defaultVisibilityTimeout: MillisVisibilityTimeout,
      deadLettersQueue: Option[DeadLettersQueueData] = None
  ) =
    QueueData(name,
              defaultVisibilityTimeout,
              Duration.ZERO,
              Duration.ZERO,
              new DateTime(0),
              new DateTime(0),
              deadLettersQueue)

  def createMessageData(id: String,
                        content: String,
                        messageAttributes: Map[String, MessageAttribute],
                        nextDelivery: MillisNextDelivery,
                        deliveryReceipt: Option[DeliveryReceipt] = None,
                        messageGroupId: Option[String] = None,
                        messageDeduplicationId: Option[String] = None) =
    MessageData(
      MessageId(id),
      deliveryReceipt,
      content,
      messageAttributes,
      nextDelivery,
      new DateTime(0),
      MessageStatistics(NeverReceived, 0),
      messageGroupId,
      messageDeduplicationId
    )

  def createNewMessageData(id: String,
                           content: String,
                           messageAttributes: Map[String, MessageAttribute],
                           nextDelivery: MillisNextDelivery,
                           messageGroupId: Option[String] = None,
                           messageDeduplicationId: Option[String] = None) =
    NewMessageData(Some(MessageId(id)),
                   content,
                   messageAttributes,
                   nextDelivery,
                   messageGroupId,
                   messageDeduplicationId)

  def createNewMessageData(messageData: MessageData) =
    NewMessageData(
      Some(messageData.id),
      messageData.content,
      messageData.messageAttributes,
      messageData.nextDelivery,
      messageData.messageGroupId,
      messageData.messageDeduplicationId
    )
}
