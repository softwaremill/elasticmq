package org.elasticmq.rest.sqs.directives

import spray.routing.{RejectionHandler, Directives}
import org.elasticmq.rest.sqs.SQSException

trait RejectionDirectives {
  this: Directives with ExceptionDirectives =>

  val rejectionHandler = RejectionHandler {
    case rejections => {
      handleServerExceptions { _ =>
        throw new SQSException("Invalid request: " + rejections.map(_.toString).mkString(", "))
      }
    }
  }

  def handleRejectionsWithSQSError = handleRejections(rejectionHandler)
}
