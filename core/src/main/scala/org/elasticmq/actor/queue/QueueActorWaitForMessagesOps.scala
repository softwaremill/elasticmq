package org.elasticmq.actor.queue

import org.elasticmq.msg.{QueueMessageMsg, ReceiveMessages, SendMessage, UpdateVisibilityTimeout}
import org.elasticmq.actor.reply._
import akka.actor.{ActorRef, Cancellable}

import scala.concurrent.{duration => scd}
import org.joda.time.Duration

import scala.annotation.tailrec

trait QueueActorWaitForMessagesOps extends ReplyingActor with QueueActorMessageOps {
  this: QueueActorStorage =>

  private var senderSequence = 0L
  private var scheduledTryReply: Option[Cancellable] = None
  private val awaitingReply =
    new collection.mutable.HashMap[Long, AwaitingData]()

  override def receive = super.receive orElse {
    case ReplyIfTimeout(seq, replyWith) => {
      awaitingReply.remove(seq).foreach {
        case AwaitingData(originalSender, _, _) =>
          logger.debug(s"${queueData.name}: Awaiting messages: sequence $seq timed out. Replying with no messages.")
          originalSender ! replyWith
      }
    }

    case TryReply =>
      scheduledTryReply = None
      tryReply()
      scheduleTryReplyWhenAvailable()
  }

  override def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = msg match {
    case SendMessage(message) =>
      val result = super.receiveAndReplyMessageMsg(msg)
      tryReply()
      scheduleTryReplyWhenAvailable()
      result

    case rm @ ReceiveMessages(_, _, waitForMessagesOpt, _) =>
      val result = super.receiveAndReplyMessageMsg(msg)
      val waitForMessages =
        waitForMessagesOpt.getOrElse(queueData.receiveMessageWait)
      if (result == ReplyWith(Nil) && waitForMessages.getMillis > 0) {
        val seq = assignSequenceFor(rm)
        logger.debug(s"${queueData.name}: Awaiting messages: start for sequence $seq.")
        scheduleTimeoutReply(seq, waitForMessages)
        scheduleTryReplyWhenAvailable()
        DoNotReply()
      } else result

    case uvm: UpdateVisibilityTimeout =>
      val result = super.receiveAndReplyMessageMsg(msg)
      tryReply()
      scheduleTryReplyWhenAvailable()
      result

    case _ => super.receiveAndReplyMessageMsg(msg)
  }

  @tailrec
  private def tryReply() {
    awaitingReply.headOption match {
      case Some(
          (seq,
           AwaitingData(originalSender, ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId), _))) =>
        val received = super.receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)

        if (received != Nil) {
          originalSender ! received
          logger.debug(
            s"${queueData.name}: Awaiting messages: replying to sequence $seq with ${received.size} messages.")
          awaitingReply.remove(seq)

          tryReply()
        }
      case _ => // do nothing
    }
  }

  private def assignSequenceFor(receiveMessages: ReceiveMessages): Long = {
    val seq = senderSequence
    senderSequence += 1
    awaitingReply(seq) = AwaitingData(sender, receiveMessages, nowProvider.nowMillis)
    seq
  }

  private def scheduleTimeoutReply(seq: Long, waitForMessages: Duration) {
    schedule(waitForMessages.getMillis, ReplyIfTimeout(seq, Nil))
  }

  private def scheduleTryReplyWhenAvailable(): Unit = {
    scheduledTryReply.foreach(_.cancel())
    scheduledTryReply = None

    // The request needs a reply and there are messages on the queue, we should try to reply. The earliest we can reply
    // is when the next message becomes available
    if (awaitingReply.nonEmpty && messageQueue.byId.nonEmpty) {
      val deliveryTime = nowProvider.nowMillis

      messageQueue.byId.values.toList.sortBy(_.nextDelivery).headOption match {
        case Some(msg) => scheduledTryReply = Some(schedule(msg.nextDelivery - deliveryTime + 1, TryReply))
        case None      =>
      }
    }
  }

  private def schedule(afterMillis: Long, msg: Any): Cancellable = {
    import context.dispatcher
    context.system.scheduler
      .scheduleOnce(scd.Duration(afterMillis, scd.MILLISECONDS), self, msg)
  }

  case class ReplyIfTimeout(seq: Long, replyWith: AnyRef)

  case class AwaitingData(originalSender: ActorRef, originalReceiveMessages: ReceiveMessages, waitStart: Long)

  case object TryReply
}
