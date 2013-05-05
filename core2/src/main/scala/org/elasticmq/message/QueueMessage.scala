package org.elasticmq.message

import org.elasticmq.actor.reply.Replyable
import org.elasticmq._
import org.elasticmq.data.{MessageDoesNotExist, QueueData, MessageData}
import org.joda.time.Duration

sealed trait QueueMessage[T] extends Replyable[T]

case class GetQueueData() extends QueueMessage[QueueData]
case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout) extends QueueMessage[Unit]
case class UpdateQueueDelay(newDelay: Duration) extends QueueMessage[Unit]

// TODO: NewMessageData; merge stats with MessageData
case class SendMessage(message: MessageData) extends QueueMessage[Unit]
case class UpdateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) extends QueueMessage[Either[MessageDoesNotExist, Unit]]
case class ReceiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery) extends QueueMessage[Option[MessageData]]
case class DeleteMessage(messageId: MessageId) extends QueueMessage[Unit]
case class LookupMessage(messageId: MessageId) extends QueueMessage[Option[MessageData]]

case class GetQueueStatistics(deliveryTime: Long) extends QueueMessage[QueueStatistics]
case class GetMessageStatistics(messageId: MessageId) extends QueueMessage[MessageStatistics]
