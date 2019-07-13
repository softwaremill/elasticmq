package org.elasticmq.msg

import akka.actor.ActorRef
import org.elasticmq._
import org.elasticmq.actor.reply.Replyable
import org.joda.time.Duration

sealed trait QueueMsg[T] extends Replyable[T]

sealed trait QueueQueueMsg[T] extends QueueMsg[T]
sealed trait QueueMessageMsg[T] extends QueueMsg[T]

case class GetQueueData() extends QueueQueueMsg[QueueData]
case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout)
    extends QueueQueueMsg[Unit]
case class UpdateQueueDelay(newDelay: Duration) extends QueueQueueMsg[Unit]
case class UpdateQueueReceiveMessageWait(newReceiveMessageWait: Duration) extends QueueQueueMsg[Unit]
case class UpdateQueueDeadLettersQueue(
    newDeadLettersQueue: Option[DeadLettersQueueData],
    newDeadLettersQueueActor: Option[ActorRef]
) extends QueueQueueMsg[Unit]
case class UpdateQueueTags(newQueueTags: Map[String, String]) extends QueueQueueMsg[Unit]
case class RemoveQueueTags(tagsToRemove: List[String]) extends QueueQueueMsg[Unit]
case class GetQueueStatistics(deliveryTime: Long) extends QueueQueueMsg[QueueStatistics]
case class ClearQueue() extends QueueQueueMsg[Unit]

case class SendMessage(message: NewMessageData) extends QueueMessageMsg[MessageData]
case class UpdateVisibilityTimeout(messageId: MessageId, visibilityTimeout: VisibilityTimeout)
    extends QueueMessageMsg[Either[MessageDoesNotExist, Unit]]
case class ReceiveMessages(
    visibilityTimeout: VisibilityTimeout,
    count: Int,
    waitForMessages: Option[Duration],
    receiveRequestAttemptId: Option[String]
) extends QueueMessageMsg[List[MessageData]]
case class DeleteMessage(deliveryReceipt: DeliveryReceipt) extends QueueMessageMsg[Unit]
case class LookupMessage(messageId: MessageId) extends QueueMessageMsg[Option[MessageData]]
