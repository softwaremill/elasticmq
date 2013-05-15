package org.elasticmq.rest.sqs

import spray.routing._
import org.elasticmq.rest.sqs.directives._
import shapeless.HNil
import com.typesafe.scalalogging.slf4j.Logging

trait ElasticMQDirectives extends Directives
  with RespondDirectives
  with FutureDirectives
  with ExceptionDirectives
  with AnyParamDirectives
  with QueueDirectives
  with QueueManagerActorModule
  with ActorSystemModule
  with Logging {

  def action(requiredActionName: String): Directive[HNil] = {
    anyParam("Action").flatMap { actionName =>
      if (actionName == requiredActionName) {
        pass
      } else {
        reject
      }
    }
  }

  def rootPath(body: Route) = {
    path("") {
      body
    }
  }
}
