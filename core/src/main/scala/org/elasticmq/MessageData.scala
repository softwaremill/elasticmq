package org.elasticmq

import java.time.OffsetDateTime

case class MessageContent(value: String) {
  override def toString: String = {
    val limit = 50
    if (value.length > limit) value.take(limit) + "..." else value
  }
}

case class MessageData(
    id: MessageId,
    deliveryReceipt: Option[DeliveryReceipt],
    content: MessageContent,
    messageAttributes: Map[String, MessageAttribute],
    nextDelivery: MillisNextDelivery,
    created: OffsetDateTime,
    statistics: MessageStatistics,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[DeduplicationId],
    tracingId: Option[TracingId],
    sequenceNumber: Option[String],
    deadLetterSourceQueueName: Option[String]
)
