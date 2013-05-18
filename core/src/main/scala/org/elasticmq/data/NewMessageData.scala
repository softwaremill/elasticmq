package org.elasticmq.data

import org.elasticmq.{NextDelivery, MessageId}

case class NewMessageData(id: Option[MessageId],
                          content: String,
                          nextDelivery: NextDelivery)
