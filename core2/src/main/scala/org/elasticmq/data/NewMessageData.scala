package org.elasticmq.data

import org.elasticmq.{NextDelivery, MessageId}
import org.joda.time.DateTime

case class NewMessageData(id: MessageId,
                          content: String,
                          nextDelivery: NextDelivery,
                          created: DateTime)
