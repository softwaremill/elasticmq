package org.elasticmq.actor.test

import org.elasticmq._
import org.elasticmq.util.OffsetDateTimeUtil

import java.time.Duration

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
    CreateQueueData(
      name = name,
      defaultVisibilityTimeout = Some(defaultVisibilityTimeout),
      delay = Some(Duration.ZERO),
      receiveMessageWait = Some(Duration.ZERO),
      created = Some(OffsetDateTimeUtil.ofEpochMilli(0)),
      lastModified = Some(OffsetDateTimeUtil.ofEpochMilli(0)),
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
      OffsetDateTimeUtil.ofEpochMilli(0),
      MessageStatistics(NeverReceived, 0),
      messageGroupId,
      messageDeduplicationId,
      tracingId,
      sequenceNumber = None,
      deadLetterSourceQueueName = None
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
      sequenceNumber = None,
      deadLetterSourceQueueName = None
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
      messageData.sequenceNumber,
      messageData.deadLetterSourceQueueName
    )
}
