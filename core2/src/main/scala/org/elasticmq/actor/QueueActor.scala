package org.elasticmq.actor

import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.actor.reply.ReplyingActor
import org.elasticmq.msg._
import scala.reflect._
import org.elasticmq.util.NowProvider
import org.elasticmq.data.QueueData

class QueueActor(val nowProvider: NowProvider, val initialQueueData: QueueData)
  extends QueueActorStorage with QueueActorQueueOps with QueueActorMessageOps with ReplyingActor with Logging {

  type M[X] = QueueMsg[X]
  val ev = classTag[M[Unit]]

  def receiveAndReply[T](msg: QueueMsg[T]) = msg match {
    case m: QueueQueueMsg[T] => receiveAndReplyQueueMsg(m)
    case m: QueueMessageMsg[T] => receiveAndReplyMessageMsg(m)
  }
}
