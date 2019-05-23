package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directive0, Directives, Rejection, RejectionHandler}
import org.elasticmq.rest.sqs.SQSException

trait RejectionDirectives {
  this: Directives with ExceptionDirectives =>

  val rejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handleAll[Rejection] { rejections =>
      handleServerExceptions { _ =>
        throw new SQSException("Invalid request: " + rejections.map(_.toString).mkString(", "))
      }
    }
    .result()

  def handleRejectionsWithSQSError: Directive0 = handleRejections(rejectionHandler)
}
