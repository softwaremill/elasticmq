package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive0, Directives, ExceptionHandler, Route}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.SQSException
import org.elasticmq.util.Logging

trait ExceptionDirectives extends Logging {
  this: Directives with RespondDirectives =>

  def handleSQSException(e: SQSException): Route = {
    respondWith(e.httpStatusCode) {
      e.toXml(EmptyRequestId)
    }
  }

  val exceptionHandlerPF: ExceptionHandler.PF = {
    case e: SQSException => handleSQSException(e)
    case e: Exception =>
      logger.error("Exception when running routes", e)
      _.complete(StatusCodes.InternalServerError)
  }

  val exceptionHandler = ExceptionHandler(exceptionHandlerPF)

  def handleServerExceptions: Directive0 = handleExceptions(exceptionHandler)
}
