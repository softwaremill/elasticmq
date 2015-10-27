package org.elasticmq.rest.sqs

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.Timeout

trait ActorSystemModule {
  implicit def actorSystem: ActorSystem
  implicit def materializer: Materializer
  implicit def messageDispatcher = actorSystem.dispatcher
  implicit def timeout: Timeout
}
