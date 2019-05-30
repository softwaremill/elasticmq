package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.{AnyParams, SQSException}
import org.elasticmq.util.Logging

trait UnmatchedActionRoutes {
  this: Logging with Directives =>

  def unmatchedAction(p: AnyParams): Route = {
    extractRequestContext { _ =>
      p.get("Action") match {
        case Some(action) =>
          logger.warn(s"Unknown action: $action")
          throw new SQSException("InvalidAction")
        case None => throw new SQSException("MissingAction")
      }
    }
  }
}
