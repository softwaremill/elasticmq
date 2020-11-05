package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.{AnyParams, SQSException}
import org.elasticmq.util.Logging

trait UnmatchedActionRoutes {
  this: Logging with Directives =>

  def unmatchedAction(p: AnyParams): Route = {
    extractUri { uri =>
      p.get("Action") match {
        case Some(action) =>
          logger.warn(
            s"Could not match request path ($uri) and associated action ($action) with any known route in ElasticMQ. Verify that given URI is a correct one."
          )
          throw new SQSException("InvalidActionOrRequestPath")
        case None =>
          throw new SQSException("MissingAction")
      }
    }
  }
}
