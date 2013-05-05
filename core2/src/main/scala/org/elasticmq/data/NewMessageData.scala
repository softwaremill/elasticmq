package org.elasticmq.data

import org.elasticmq.{MillisNextDelivery, MessageId}
import org.joda.time.DateTime

case class NewMessageData(id: MessageId,
                          content: String,
                          nextDelivery: MillisNextDelivery,
                          created: DateTime)
