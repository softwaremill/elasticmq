package org.elasticmq


case class NewMessageData(id: Option[MessageId],
                          content: String,
                          messageAttributes: Map[String, MessageAttribute],
                          nextDelivery: NextDelivery,
                          messageGroupId: Option[String],
                          messageDeduplicationId: Option[String])
