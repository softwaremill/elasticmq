package org.elasticmq.actor.queue

import akka.actor.Timers
import org.elasticmq.actor.queue.operations._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{DeleteMessage, LookupMessage, ReceiveMessages, SendMessage, UpdateVisibilityTimeout, _}
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.duration.DurationInt

trait QueueActorMessageOps
    extends Logging
    with SendMessageOp
    with UpdateVisibilityTimeoutOps
    with DeleteMessageOps
    with ReceiveMessageOps
    with MoveMessageOps
    with Timers {
  this: QueueActorStorage =>

  def nowProvider: NowProvider
  def context: akka.actor.ActorContext

  timers.startTimerWithFixedDelay(s"Timer: ${queueData.name}", DeduplicationIdsCleanup, 1.second)

  def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] =
    msg match {
      case SendMessage(message) => handleOrRedirectMessage(message, context)
      case UpdateVisibilityTimeout(messageId, visibilityTimeout) =>
        updateVisibilityTimeout(messageId, visibilityTimeout)
      case ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId) =>
        receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
      case DeleteMessage(deliveryReceipt) => deleteMessage(deliveryReceipt)
      case LookupMessage(messageId)       => messageQueue.byId.get(messageId.id).map(_.toMessageData)
      case MoveMessage(message)           => moveMessage(message)
      case DeduplicationIdsCleanup =>
        fifoMessagesHistory = fifoMessagesHistory.cleanOutdatedMessages(nowProvider)
        DoNotReply()
    }
}
