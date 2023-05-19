package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.model.RequestPayload
import org.elasticmq.rest.sqs.{Action, AnyParams, SQSException}
import org.elasticmq.util.Logging

trait UnmatchedActionRoutes {
  this: Logging with Directives =>

  def unmatchedAction(p: RequestPayload): Route = {
    extractRequestContext { _ =>
      p.maybeAction match {
        case Some(action) if Action.values.forall(_.toString != action) =>
          logger.warn(s"Unknown action: $action")
          throw new SQSException("InvalidAction")
        case None =>
          throw new SQSException("MissingAction")
        case _ =>
          reject
      }
    }
  }
}
