package org.elasticmq


case class NewMessageData(id: Option[MessageId],
                          content: String,
                          messageAttributes: Map[String, String],
                          nextDelivery: NextDelivery)
