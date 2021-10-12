package org.elasticmq.actor.queue

import akka.actor.ActorRef
import org.elasticmq.QueueData
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.msg._
import org.elasticmq.util.{Logging, NowProvider}

import scala.reflect._

class QueueActor(
    val nowProvider: NowProvider,
    val initialQueueData: QueueData,
    var deadLettersActorRef: Option[ActorRef] = None,
    val copyMessagesToActorRef: Option[ActorRef] = None,
    val moveMessagesToActorRef: Option[ActorRef] = None,
    val queueEventListener: Option[ActorRef] = None
) extends QueueActorStorage
    with QueueActorQueueOps
    with QueueActorWaitForMessagesOps
    with ReplyingActor
    with Logging {

  type M[X] = QueueMsg[X]
  val ev = classTag[M[Unit]]

  def receiveAndReply[T](msg: QueueMsg[T]) =
    msg match {
      case m: QueueQueueMsg[T] =>
        val replyAction = receiveAndReplyQueueMsg(m)
        if (m.updatesQueueMetadata) queueEventListener.foreach(_ ! QueueMetadataUpdated(queueData))
        replyAction
      case m: QueueMessageMsg[T] => receiveAndReplyMessageMsg(m)
    }
}
