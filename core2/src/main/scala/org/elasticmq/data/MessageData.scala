package org.elasticmq.data

import org.elasticmq.{DeliveryReceipt, MessageId, MillisNextDelivery}
import org.joda.time.DateTime

case class MessageData(id: MessageId,
                       deliveryReceipt: Option[DeliveryReceipt],
                       content: String,
                       nextDelivery: MillisNextDelivery,
                       created: DateTime)