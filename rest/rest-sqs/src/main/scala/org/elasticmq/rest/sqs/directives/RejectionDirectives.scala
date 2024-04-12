package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.server.{Directive0, Directives, Rejection, RejectionHandler}
import org.elasticmq.rest.sqs.{AWSProtocol, SQSException}

trait RejectionDirectives {
  this: Directives with ExceptionDirectives =>

  def rejectionHandler(protocol: AWSProtocol): RejectionHandler = RejectionHandler
    .newBuilder()
    .handleAll[Rejection] { rejections =>
      handleServerExceptions(protocol) { _ =>
        throw SQSException.invalidAction("Invalid request: " + rejections.map(_.toString).mkString(", "))
      }
    }
    .result()

  def handleRejectionsWithSQSError(protocol: AWSProtocol): Directive0 = handleRejections(rejectionHandler(protocol))
}
