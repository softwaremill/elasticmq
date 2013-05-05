package org.elasticmq.msg

import org.elasticmq.actor.reply.Replyable
import org.elasticmq._
import org.elasticmq.data.{NewMessageData, MessageDoesNotExist, QueueData, MessageData}
import org.joda.time.Duration

sealed trait QueueMsg[T] extends Replyable[T]

case class GetQueueData() extends QueueMsg[QueueData]
case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout) extends QueueMsg[Unit]
case class UpdateQueueDelay(newDelay: Duration) extends QueueMsg[Unit]

case class SendMessage(message: NewMessageData) extends QueueMsg[Unit]
case class UpdateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) extends QueueMsg[Either[MessageDoesNotExist, Unit]]
case class ReceiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery) extends QueueMsg[Option[MessageData]]
case class DeleteMessage(messageId: MessageId) extends QueueMsg[Unit]
case class LookupMessage(messageId: MessageId) extends QueueMsg[Option[MessageData]]

case class GetQueueStatistics(deliveryTime: Long) extends QueueMsg[QueueStatistics]
