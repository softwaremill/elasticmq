package org.elasticmq.message

import org.elasticmq.actor.reply.Replyable
import org.elasticmq._
import org.elasticmq.data.MessageData
import org.joda.time.Duration

sealed trait QueueMessage[T] extends Replyable[T]

case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout) extends QueueMessage[Unit]
case class UpdateQueueDelay(newDelay: Duration) extends QueueMessage[Unit]

case class SendMessage(message: MessageData) extends QueueMessage[Unit]
case class UpdateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) extends QueueMessage[Unit]
case class ReceiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery) extends QueueMessage[Option[MessageData]]
case class DeleteMessage(messageId: MessageId) extends QueueMessage[Unit]
case class LookupMessage(messageId: MessageId) extends QueueMessage[Option[MessageData]]

case class GetQueueStatistics(name: String, deliveryTime: Long) extends QueueMessage[QueueStatistics]
case class GetMessageStatistics(messageId: MessageId) extends QueueMessage[MessageStatistics]
