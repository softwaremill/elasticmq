package org.elasticmq.rest.sqs

import akka.actor.ActorSystem
import akka.util.Timeout

trait ActorSystemModule {
  def actorSystem: ActorSystem

  implicit def messageDispatcher = actorSystem.dispatcher

  implicit def timeout: Timeout
}
