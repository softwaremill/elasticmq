package org.elasticmq


case class NewMessageData(id: Option[MessageId],
                          content: String,
                          nextDelivery: NextDelivery)
