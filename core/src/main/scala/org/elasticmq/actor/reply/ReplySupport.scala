package org.elasticmq.actor.reply

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.util.Timeout
import org.apache.pekko.pattern
import scala.concurrent.Future
import scala.reflect.ClassTag

trait ReplySupport {
  implicit class ReplyActorRef(actorRef: ActorRef) {
    def ?[T](message: Replyable[T])(implicit timeout: Timeout, tag: ClassTag[T]): Future[T] = {
      pattern.ask(actorRef, message).mapTo[T]
    }
  }

  implicit def valueToReplyWith[T](t: T): ReplyWith[T] = ReplyWith(t)
}
