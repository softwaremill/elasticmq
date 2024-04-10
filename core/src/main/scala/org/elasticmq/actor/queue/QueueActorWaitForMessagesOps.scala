package org.elasticmq.actor.queue

import org.apache.pekko.actor.{ActorRef, Cancellable}
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{QueueMessageMsg, ReceiveMessages, SendMessage, UpdateVisibilityTimeout}

import java.time.Duration
import scala.annotation.tailrec
import scala.concurrent.{duration => scd}

trait QueueActorWaitForMessagesOps extends ReplyingActor with QueueActorMessageOps {
  this: QueueActorStorage =>

  private var senderSequence = 0L
  private var scheduledTryReply: Option[Cancellable] = None
  private val awaitingReply = new collection.mutable.HashMap[Long, AwaitingData]()

  override def receive =
    super.receive orElse {
      case ReplyIfTimeout(seq, replyWith) =>
        awaitingReply.remove(seq).foreach { case AwaitingData(originalSender, _, _) =>
          logger.debug(s"${queueData.name}: Awaiting messages: sequence $seq timed out. Replying with no messages.")
          originalSender ! replyWith
        }

      case TryReply =>
        scheduledTryReply = None
        tryReply()
        scheduleTryReplyWhenAvailable()
    }

  override def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = {
    msg match {
      case SendMessage(_) =>
        val result = super.receiveAndReplyMessageMsg(msg)
        tryReply()
        scheduleTryReplyWhenAvailable()
        result

      case rm @ ReceiveMessages(visibilityTimeout, count, waitForMessagesOpt, receiveRequestAttemptId) =>
        val result = receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
        val waitForMessages = waitForMessagesOpt.getOrElse(queueData.receiveMessageWait)

        if (result.result.contains(Nil) && waitForMessages.toMillis > 0) {
          val seq = assignSequenceFor(rm, context.sender())
          logger.debug(s"${queueData.name}: Awaiting messages: start for sequence $seq.")
          scheduleTimeoutReply(seq, waitForMessages)
          scheduleTryReplyWhenAvailable()
          DoNotReply()
        } else
          result.send()

      case _: UpdateVisibilityTimeout =>
        val result = super.receiveAndReplyMessageMsg(msg)
        tryReply()
        scheduleTryReplyWhenAvailable()
        result

      case _ => super.receiveAndReplyMessageMsg(msg)
    }
  }

  @tailrec
  private def tryReply(): Unit = {
    awaitingReply.headOption match {
      case Some(
            (
              seq,
              AwaitingData(originalSender, ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId), _)
            )
          ) =>
        val result = receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
        if (!result.result.contains(Nil)) {
          logger.debug(
            s"${queueData.name}: Awaiting messages: replying to sequence $seq with ${result.result.get.size} messages."
          )
          result.send(Some(originalSender))
          awaitingReply.remove(seq)
          tryReply()
        }
      case _ => // do nothing
    }
  }

  private def assignSequenceFor(receiveMessages: ReceiveMessages, recipient: ActorRef): Long = {
    val seq = senderSequence
    senderSequence += 1
    awaitingReply(seq) = AwaitingData(recipient, receiveMessages, nowProvider.nowMillis)
    seq
  }

  private def scheduleTimeoutReply(seq: Long, waitForMessages: Duration): Unit = {
    schedule(waitForMessages.toMillis, ReplyIfTimeout(seq, Nil))
    ()
  }

  private def scheduleTryReplyWhenAvailable(): Unit = {
    scheduledTryReply.foreach(_.cancel())
    scheduledTryReply = None

    // The request needs a reply and there are messages on the queue, we should try to reply. The earliest we can reply
    // is when the next message becomes available
    if (awaitingReply.nonEmpty && messageQueue.all.nonEmpty) {
      val deliveryTime = nowProvider.nowMillis

      messageQueue.all.toList.sortBy(_.nextDelivery).headOption match {
        case Some(msg) => scheduledTryReply = Some(schedule(msg.nextDelivery - deliveryTime + 1, TryReply))
        case None      =>
      }
    }
  }

  private def schedule(afterMillis: Long, msg: Any): Cancellable = {
    context.system.scheduler
      .scheduleOnce(scd.Duration(afterMillis, scd.MILLISECONDS), self, msg)
  }

  case class ReplyIfTimeout(seq: Long, replyWith: AnyRef)

  case class AwaitingData(originalSender: ActorRef, originalReceiveMessages: ReceiveMessages, waitStart: Long)

  case object TryReply
}
