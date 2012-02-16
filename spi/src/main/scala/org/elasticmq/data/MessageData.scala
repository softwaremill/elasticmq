package org.elasticmq.data

import org.elasticmq.{MessageId, MillisNextDelivery}
import org.joda.time.DateTime

case class MessageData(id: MessageId,
                       content: String,
                       nextDelivery: MillisNextDelivery,
                       created: DateTime)