package org.elasticmq.actor.queue

import org.apache.pekko.actor.Timers
import org.elasticmq.actor.queue.operations._
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.duration.DurationInt

trait QueueActorMessageOps
    extends Logging
    with SendMessageOp
    with UpdateVisibilityTimeoutOps
    with DeleteMessageOps
    with ReceiveMessageOps
    with MoveMessageOps
    with MoveMessagesAsyncOps
    with Timers {
  this: QueueActorStorage =>

  def nowProvider: NowProvider
  def context: org.apache.pekko.actor.ActorContext

  timers.startTimerWithFixedDelay(s"Timer: ${queueData.name}", DeduplicationIdsCleanup, 1.second)

  def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = {
    msg match {
      case SendMessage(message) =>
        handleOrRedirectMessage(message, context).send()
      case UpdateVisibilityTimeout(deliveryReceipt, visibilityTimeout) =>
        updateVisibilityTimeout(deliveryReceipt, visibilityTimeout).send()
      case ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId) =>
        receiveMessages(visibilityTimeout, count, receiveRequestAttemptId).send()
      case DeleteMessage(deliveryReceipt) =>
        deleteMessage(deliveryReceipt).send()
      case LookupMessage(messageId)          => messageQueue.getById(messageId.id).map(_.toMessageData)
      case MoveMessage(message, destination) => moveMessage(message, destination).send()
      case DeduplicationIdsCleanup =>
        fifoMessagesHistory = fifoMessagesHistory.cleanOutdatedMessages(nowProvider)
        DoNotReply()
      case RestoreMessages(messages) => restoreMessages(messages)
      case StartMessageMoveTaskToQueue(destinationQueue, maxNumberOfMessagesPerSecond) =>
        startMovingMessages(destinationQueue, maxNumberOfMessagesPerSecond)
      case MoveFirstMessageToQueue(destinationQueue, maxNumberOfMessagesPerSecond) =>
        moveFirstMessage(destinationQueue, maxNumberOfMessagesPerSecond).send()
    }
  }
}
