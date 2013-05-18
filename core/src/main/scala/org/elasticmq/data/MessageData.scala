package org.elasticmq.data

import org.elasticmq.{MessageStatistics, DeliveryReceipt, MessageId, MillisNextDelivery}
import org.joda.time.DateTime

case class MessageData(id: MessageId,
                       deliveryReceipt: Option[DeliveryReceipt],
                       content: String,
                       nextDelivery: MillisNextDelivery,
                       created: DateTime,
                       statistics: MessageStatistics)