package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.util.Logging
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.msg._

import scala.reflect._
import org.elasticmq.util.NowProvider
import org.elasticmq.QueueData

class QueueActor(
  val nowProvider: NowProvider,
  val initialQueueData: QueueData,
  val deadLettersActorRef: Option[ActorRef])
  extends QueueActorStorage with QueueActorQueueOps with QueueActorWaitForMessagesOps with ReplyingActor with Logging {

  type M[X] = QueueMsg[X]
  val ev = classTag[M[Unit]]

  def receiveAndReply[T](msg: QueueMsg[T]) = msg match {
    case m: QueueQueueMsg[T] => receiveAndReplyQueueMsg(m)
    case m: QueueMessageMsg[T] => receiveAndReplyMessageMsg(m)
  }
}
