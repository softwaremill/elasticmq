package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule}
import org.elasticmq.util.Logging

trait ElasticMQDirectives
    extends Directives
    with RespondDirectives
    with FutureDirectives
    with ExceptionDirectives
    with QueueDirectives
    with QueueManagerActorModule
    with ActorSystemModule
    with AnyParamDirectives
    with RejectionDirectives
    with Logging {

  def rootPath(body: Route) = {
    path("") {
      body
    }
  }
}
