package org.elasticmq.msg

import org.elasticmq.actor.reply.Replyable
import org.elasticmq._
import org.elasticmq.data.{NewMessageData, MessageDoesNotExist, QueueData, MessageData}
import org.joda.time.Duration

sealed trait QueueMsg[T] extends Replyable[T]

sealed trait QueueQueueMsg[T] extends QueueMsg[T]
sealed trait QueueMessageMsg[T] extends QueueMsg[T]

case class GetQueueData() extends QueueQueueMsg[QueueData]
case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout) extends QueueQueueMsg[Unit]
case class UpdateQueueDelay(newDelay: Duration) extends QueueQueueMsg[Unit]
case class GetQueueStatistics(deliveryTime: Long) extends QueueQueueMsg[QueueStatistics]

case class SendMessage(message: NewMessageData) extends QueueMessageMsg[Unit]
case class UpdateVisibilityTimeout(messageId: MessageId, visibilityTimeout: VisibilityTimeout) extends QueueMessageMsg[Either[MessageDoesNotExist, Unit]]
case class ReceiveMessage(deliveryTime: Long, visibilityTimeout: VisibilityTimeout) extends QueueMessageMsg[Option[MessageData]]
case class DeleteMessage(messageId: MessageId) extends QueueMessageMsg[Unit]
case class LookupMessage(messageId: MessageId) extends QueueMessageMsg[Option[MessageData]]
