package org.elasticmq

import org.joda.time.DateTime

case class MessageData(
    id: MessageId,
    deliveryReceipt: Option[DeliveryReceipt],
    content: String,
    messageAttributes: Map[String, MessageAttribute],
    nextDelivery: MillisNextDelivery,
    created: DateTime,
    statistics: MessageStatistics,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[DeduplicationId],
    tracingId: Option[TracingId]
)
