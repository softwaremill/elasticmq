package org.elasticmq.actor.test

import org.elasticmq._
import org.joda.time.{DateTime, Duration}

trait DataCreationHelpers {
  def createQueueData(
      name: String,
      defaultVisibilityTimeout: MillisVisibilityTimeout,
      deadLettersQueue: Option[DeadLettersQueueData] = None,
      copyMessagesToQueue: Option[String] = None,
      moveMessagesToQueue: Option[String] = None,
      tags: Map[String, String] = Map[String, String](),
      isFifo: Boolean = false,
      hasContentBasedDeduplication: Boolean = false
  ) =
    CreateQueueRequest(
      name = name,
      defaultVisibilityTimeout = Some(defaultVisibilityTimeout),
      delay = Some(Duration.ZERO),
      receiveMessageWait = Some(Duration.ZERO),
      created = Some(new DateTime(0)),
      lastModified = Some(new DateTime(0)),
      deadLettersQueue = deadLettersQueue,
      copyMessagesTo = copyMessagesToQueue,
      moveMessagesTo = moveMessagesToQueue,
      tags = tags,
      isFifo = isFifo,
      hasContentBasedDeduplication = hasContentBasedDeduplication
    )

  def createMessageData(
      id: String,
      content: String,
      messageAttributes: Map[String, MessageAttribute],
      nextDelivery: MillisNextDelivery,
      deliveryReceipt: Option[DeliveryReceipt] = None,
      messageGroupId: Option[String] = None,
      messageDeduplicationId: Option[DeduplicationId] = None,
      tracingId: Option[TracingId] = None
  ) =
    MessageData(
      MessageId(id),
      deliveryReceipt,
      content,
      messageAttributes,
      nextDelivery,
      new DateTime(0),
      MessageStatistics(NeverReceived, 0),
      messageGroupId,
      messageDeduplicationId,
      tracingId,
      None
    )

  def createNewMessageData(
      id: String,
      content: String,
      messageAttributes: Map[String, MessageAttribute],
      nextDelivery: MillisNextDelivery,
      messageGroupId: Option[String] = None,
      messageDeduplicationId: Option[DeduplicationId] = None,
      tracingId: Option[TracingId] = None
  ) =
    NewMessageData(
      Some(MessageId(id)),
      content,
      messageAttributes,
      nextDelivery,
      messageGroupId,
      messageDeduplicationId,
      orderIndex = 0,
      tracingId,
      None
    )

  def createNewMessageData(messageData: MessageData) =
    NewMessageData(
      Some(messageData.id),
      messageData.content,
      messageData.messageAttributes,
      messageData.nextDelivery,
      messageData.messageGroupId,
      messageData.messageDeduplicationId,
      orderIndex = 0,
      messageData.tracingId,
      messageData.sequenceNumber
    )
}
