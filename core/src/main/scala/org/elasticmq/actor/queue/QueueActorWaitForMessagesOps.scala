package org.elasticmq.actor.queue

import akka.actor.{ActorRef, Cancellable}
import org.elasticmq.MessageData
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{QueueMessageMsg, ReceiveMessages, SendMessage, UpdateVisibilityTimeout}
import org.joda.time.Duration

import scala.concurrent.{duration => scd}

trait QueueActorWaitForMessagesOps extends ReplyingActor with QueueActorMessageOps {
  this: QueueActorStorage =>

  private var senderSequence = 0L
  private var scheduledTryReply: Option[Cancellable] = None
  private val awaitingReply = new collection.mutable.HashMap[Long, AwaitingData]()

  override def receive =
    super.receive orElse {
      case AwaitMessages(rm, recipient) =>
        val waitForMessages = rm.waitForMessages.getOrElse(queueData.receiveMessageWait)
        val seq = assignSequenceFor(rm, recipient)
        logger.debug(s"${queueData.name}: Awaiting messages: start for sequence $seq.")
        scheduleTimeoutReply(seq, waitForMessages)
        scheduleTryReplyWhenAvailable()

      case ReAwaitMessages(seq, ad) =>
        awaitingReply.put(seq, ad.copy(pending = false))

      case ReplyIfTimeout(seq, replyWith) =>
        awaitingReply.remove(seq).foreach { case AwaitingData(originalSender, _, _, _) =>
          logger.debug(s"${queueData.name}: Awaiting messages: sequence $seq timed out. Replying with no messages.")
          originalSender ! replyWith
        }

      case TryReply =>
        scheduledTryReply = None
        tryReply()
        scheduleTryReplyWhenAvailable()

      case SendReply(seq, originalSender, messages) =>
        awaitingReply.remove(seq)
        sendReply(seq, originalSender, messages)
        tryReply()
    }

  override def receiveAndReplyMessageMsg[T](msg: QueueMessageMsg[T]): ReplyAction[T] = {
    msg match {
      case SendMessage(message) =>
        val result = super.receiveAndReplyMessageMsg(msg)
        tryReply()
        scheduleTryReplyWhenAvailable()
        result

      case rm @ ReceiveMessages(visibilityTimeout, count, waitForMessagesOpt, receiveRequestAttemptId) =>
        val result = receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
        val waitForMessages = waitForMessagesOpt.getOrElse(queueData.receiveMessageWait)
        val recipient = context.sender()
        result.map { messages =>
          if (messages == Nil && waitForMessages.getMillis > 0)
            self ! AwaitMessages(rm, recipient)
          else
            recipient ! messages
        }
        DoNotReply()

      case uvm: UpdateVisibilityTimeout =>
        val result = super.receiveAndReplyMessageMsg(msg)
        tryReply()
        scheduleTryReplyWhenAvailable()
        result

      case _ => super.receiveAndReplyMessageMsg(msg)
    }
  }

  private def tryReply(): Unit = {
    awaitingReply.headOption match {
      case Some(
            (
              seq,
              ad @ AwaitingData(originalSender, ReceiveMessages(visibilityTimeout, count, _, receiveRequestAttemptId), _, pending)
            )
          ) =>
        if (!pending) {
          val received = receiveMessages(visibilityTimeout, count, receiveRequestAttemptId)
          awaitingReply.put(seq, ad.copy(pending = true))

          received.map { messages =>
            if (messages != Nil)
              self ! SendReply(seq, originalSender, messages)
            else
              self ! ReAwaitMessages(seq, ad)
          }
        }
      case _ => // do nothing
    }
  }

  protected def sendReply(seq: Long, recipient: ActorRef, messages: List[MessageData]): Unit = {
    recipient ! messages
    logger.debug(
      s"${queueData.name}: Awaiting messages: replying to sequence $seq with ${messages.size} messages."
    )
  }

  private def assignSequenceFor(receiveMessages: ReceiveMessages, recipient: ActorRef): Long = {
    val seq = senderSequence
    senderSequence += 1
    awaitingReply(seq) = AwaitingData(recipient, receiveMessages, nowProvider.nowMillis)
    seq
  }

  private def scheduleTimeoutReply(seq: Long, waitForMessages: Duration): Unit = {
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
    context.system.scheduler
      .scheduleOnce(scd.Duration(afterMillis, scd.MILLISECONDS), self, msg)
  }

  case class ReplyIfTimeout(seq: Long, replyWith: AnyRef)

  case class AwaitingData(originalSender: ActorRef, originalReceiveMessages: ReceiveMessages, waitStart: Long, pending: Boolean = false)

  case class AwaitMessages(rm: ReceiveMessages, recipient: ActorRef)

  case class ReAwaitMessages(seq: Long, ad: AwaitingData)

  case class SendReply(seq: Long, recipient: ActorRef, messages: List[MessageData])

  case object TryReply
}
