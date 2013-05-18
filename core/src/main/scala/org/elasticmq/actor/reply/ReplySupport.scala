package org.elasticmq.actor.reply

import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.Future
import scala.reflect.ClassTag

trait ReplySupport {
  implicit class ReplyActorRef(actorRef: ActorRef) {
    def ?[T](message: Replyable[T])(implicit timeout: Timeout, tag: ClassTag[T]): Future[T] = {
      akka.pattern.ask(actorRef, message).mapTo[T]
    }
  }
}
