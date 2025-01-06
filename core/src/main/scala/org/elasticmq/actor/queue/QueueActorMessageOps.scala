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
      case LookupMessage(messageId) => messageQueue.getById(messageId.id).map(_.toMessageData)
      case MoveMessage(message, destination, sourceQueueName) =>
        moveMessage(message, destination, sourceQueueName).send()
      case DeduplicationIdsCleanup =>
        fifoMessagesHistory = fifoMessagesHistory.cleanOutdatedMessages(nowProvider)
        DoNotReply()
      case RestoreMessages(messages) => restoreMessages(messages)
      case StartMovingMessages(
            destinationQueue,
            destinationArn,
            sourceArn,
            maxNumberOfMessagesPerSecond,
            queueManager
          ) =>
        startMovingMessages(destinationQueue, destinationArn, sourceArn, maxNumberOfMessagesPerSecond, queueManager)
      case CancelMovingMessages() =>
        cancelMovingMessages()
      case MoveFirstMessage(destinationQueue, queueManager) =>
        moveFirstMessage(destinationQueue, queueManager).send()
      case GetMovingMessagesTasks() =>
        getMovingMessagesTasks
    }
  }
}
