package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directive0, Directives, ExceptionHandler, Route}
import org.elasticmq.rest.sqs.{AWSProtocol, SQSException}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.util.Logging
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

trait ExceptionDirectives extends Logging {
  this: Directives with RespondDirectives =>

  private def handleSQSException(e: SQSException, protocol: AWSProtocol): Route = {
    protocol match {
      case AWSProtocol.AWSQueryProtocol =>
        respondWith(e.httpStatusCode) {
          e.toXml(EmptyRequestId)
        }
      case _ => complete(e.httpStatusCode, ErrorResponse(e.errorType, e.message))
    }
  }

  def exceptionHandlerPF(protocol: AWSProtocol): ExceptionHandler.PF = {
    case e: SQSException =>
      handleSQSException(e, protocol)
    case e: Exception =>
      logger.error("Exception when running routes", e)
      _.complete(StatusCodes.InternalServerError)
  }

  private def exceptionHandler(protocol: AWSProtocol): ExceptionHandler = ExceptionHandler(exceptionHandlerPF(protocol))

  def handleServerExceptions(protocol: AWSProtocol): Directive0 = handleExceptions(exceptionHandler(protocol))

  case class ErrorResponse(`__type`: String, Message: String)
  object ErrorResponse {
    implicit val format: RootJsonFormat[ErrorResponse] = jsonFormat2(ErrorResponse.apply)
  }

}
