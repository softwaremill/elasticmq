package org.elasticmq.rest.sqs

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Timeout
import scala.concurrent.ExecutionContextExecutor

trait ActorSystemModule {
  implicit def actorSystem: ActorSystem
  implicit def materializer: Materializer
  implicit def messageDispatcher: ExecutionContextExecutor = actorSystem.dispatcher
  implicit def timeout: Timeout
}
