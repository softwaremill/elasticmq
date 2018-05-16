package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Rejection, RejectionHandler, Directives}
import org.elasticmq.rest.sqs.SQSException

trait RejectionDirectives {
  this: Directives with ExceptionDirectives =>

  val rejectionHandler = RejectionHandler
    .newBuilder()
    .handleAll[Rejection] {
      case rejections =>
        handleServerExceptions { _ =>
          throw new SQSException("Invalid request: " + rejections.map(_.toString).mkString(", "))
        }
    }
    .result()

  def handleRejectionsWithSQSError = handleRejections(rejectionHandler)
}
