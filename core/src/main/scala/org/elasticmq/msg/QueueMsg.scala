package org.elasticmq.msg

import org.apache.pekko.actor.ActorRef
import org.elasticmq._
import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.actor.reply.Replyable

import java.time.Duration

sealed trait QueueMsg[T] extends Replyable[T]

sealed trait QueueQueueMsg[T] extends QueueMsg[T] {
  val updatesQueueMetadata: Boolean = false
}
sealed trait QueueMessageMsg[T] extends QueueMsg[T]

case class GetQueueData() extends QueueQueueMsg[QueueData]
case class UpdateQueueDefaultVisibilityTimeout(newDefaultVisibilityTimeout: MillisVisibilityTimeout)
    extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class UpdateQueueDelay(newDelay: Duration) extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class UpdateQueueReceiveMessageWait(newReceiveMessageWait: Duration) extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class UpdateQueueDeadLettersQueue(
    newDeadLettersQueue: Option[DeadLettersQueueData],
    newDeadLettersQueueActor: Option[ActorRef]
) extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class UpdateQueueTags(newQueueTags: Map[String, String]) extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class RemoveQueueTags(tagsToRemove: List[String]) extends QueueQueueMsg[Unit] {
  override val updatesQueueMetadata: Boolean = true
}
case class GetQueueStatistics(deliveryTime: Long) extends QueueQueueMsg[QueueStatistics]
case class ClearQueue() extends QueueQueueMsg[Unit]

case class SendMessage(message: NewMessageData) extends QueueMessageMsg[MessageData]
case class MoveMessage(message: InternalMessage, moveDestination: MoveDestination) extends QueueMessageMsg[Unit]
case class UpdateVisibilityTimeout(deliveryReceipt: DeliveryReceipt, visibilityTimeout: VisibilityTimeout)
    extends QueueMessageMsg[Either[InvalidReceiptHandle, Unit]]
case class ReceiveMessages(
    visibilityTimeout: VisibilityTimeout,
    count: Int,
    waitForMessages: Option[Duration],
    receiveRequestAttemptId: Option[String]
) extends QueueMessageMsg[List[MessageData]]
case class DeleteMessage(deliveryReceipt: DeliveryReceipt) extends QueueMessageMsg[Either[InvalidReceiptHandle, Unit]]
case class LookupMessage(messageId: MessageId) extends QueueMessageMsg[Option[MessageData]]
case object DeduplicationIdsCleanup extends QueueMessageMsg[Unit]
case class RestoreMessages(messages: List[InternalMessage]) extends QueueMessageMsg[Unit]
case class StartMessageMoveTaskToQueue(destinationQueue: ActorRef, maxNumberOfMessagesPerSecond: Option[Int])
    extends QueueMessageMsg[StartMessageMoveTaskId]

case class MoveFirstMessageToQueue(destinationQueue: ActorRef, maxNumberOfMessagesPerSecond: Option[Int])
  extends QueueMessageMsg[Unit]
