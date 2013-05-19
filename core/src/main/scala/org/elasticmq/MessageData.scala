package org.elasticmq

import org.joda.time.DateTime

case class MessageData(id: MessageId,
                       deliveryReceipt: Option[DeliveryReceipt],
                       content: String,
                       nextDelivery: MillisNextDelivery,
                       created: DateTime,
                       statistics: MessageStatistics)