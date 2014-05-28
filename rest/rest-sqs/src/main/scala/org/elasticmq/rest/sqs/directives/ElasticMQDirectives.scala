package org.elasticmq.rest.sqs.directives

import spray.routing._
import org.elasticmq.rest.sqs.directives._
import shapeless.HNil
import org.elasticmq.util.Logging
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule}
import spray.routing.directives.ParameterDirectives._
import spray.routing.directives.MarshallingDirectives._
import spray.http.FormData

trait ElasticMQDirectives extends Directives
  with RespondDirectives
  with FutureDirectives
  with ExceptionDirectives
  with QueueDirectives
  with QueueManagerActorModule
  with ActorSystemModule
  with AnyParamDirectives2
  with RejectionDirectives
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
