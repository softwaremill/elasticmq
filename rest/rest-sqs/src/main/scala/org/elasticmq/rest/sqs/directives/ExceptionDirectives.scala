package org.elasticmq.rest.sqs.directives

import org.elasticmq.rest.sqs.SQSException
import org.elasticmq.rest.sqs.Constants._
import spray.routing.{Directives, ExceptionHandler}
import spray.http.StatusCodes
import com.typesafe.scalalogging.slf4j.Logging

trait ExceptionDirectives extends Logging {
  this: Directives with RespondDirectives =>

  def handleSQSException(e: SQSException) = {
    respondWith(e.httpStatusCode) {
      e.toXml(EmptyRequestId)
    }
  }

  val exceptionHandlerPF: ExceptionHandler.PF = {
    case e: SQSException => handleSQSException(e)
    case e: Exception => {
      logger.error("Exception when running routes", e)
      _.complete(StatusCodes.InternalServerError)
    }
  }

  val exceptionHandler = ExceptionHandler.fromPF(exceptionHandlerPF)

  def handleServerExceptions = handleExceptions(exceptionHandler)
}
