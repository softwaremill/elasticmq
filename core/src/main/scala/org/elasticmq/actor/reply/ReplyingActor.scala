package org.elasticmq.actor.reply

import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.Status.Failure
import scala.reflect.ClassTag
import scala.language.higherKinds

trait ReplyingActor extends Actor {
  type M[X] <: Replyable[X]
  val ev: ClassTag[M[Unit]]

  def receive = {
    case m if ev.runtimeClass.isAssignableFrom(m.getClass) =>
      doReceiveAndReply(m.asInstanceOf[M[Unit]])
  }

  private def doReceiveAndReply[T](msg: M[T]): Unit = {
    try {
      receiveAndReply(msg) match {
        case ReplyWith(t) => sender ! t
        case DoNotReply() => // do nothing
      }
    } catch {
      case e: Exception => sender ! Failure(e)
    }
  }

  def receiveAndReply[T](msg: M[T]): ReplyAction[T]
}
