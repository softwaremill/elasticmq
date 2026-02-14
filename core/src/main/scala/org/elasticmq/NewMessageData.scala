package org.elasticmq

case class NewMessageData(
    id: Option[MessageId],
    content: MessageContent,
    messageAttributes: Map[String, MessageAttribute],
    nextDelivery: NextDelivery,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[DeduplicationId],
    orderIndex: Int,
    tracingId: Option[TracingId],
    sequenceNumber: Option[String],
    deadLetterSourceQueueName: Option[String]
)
