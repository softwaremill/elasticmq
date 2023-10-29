package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.model.RequestPayload
import org.elasticmq.rest.sqs.{Action, AnyParams, SQSException}
import org.elasticmq.util.Logging

trait UnmatchedActionRoutes {
  this: Logging with Directives =>

  def unmatchedAction(p: RequestPayload): Route = {
    extractRequestContext { _ =>
      if (Action.values.forall(_.toString != p.action)) {
        logger.warn(s"Unknown action: ${p.action}")
        throw new SQSException("InvalidAction")
      } else {
        reject
      }
    }
  }
}
