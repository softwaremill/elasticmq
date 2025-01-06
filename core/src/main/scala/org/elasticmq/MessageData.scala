package org.elasticmq

import java.time.OffsetDateTime

case class MessageData(
    id: MessageId,
    deliveryReceipt: Option[DeliveryReceipt],
    content: String,
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
