package org.elasticmq

import org.joda.time.DateTime

case class MessageData(id: MessageId,
                       deliveryReceipt: Option[DeliveryReceipt],
                       content: String,
                       messageAttributes: Map[String, String],
                       nextDelivery: MillisNextDelivery,
                       created: DateTime,
                       statistics: MessageStatistics)