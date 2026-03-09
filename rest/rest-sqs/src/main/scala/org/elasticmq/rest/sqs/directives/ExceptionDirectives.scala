package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import org.apache.pekko.http.scaladsl.server.{Directive0, Directives, ExceptionHandler, Route}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.{AWSProtocol, SQSException}
import org.elasticmq.util.Logging
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.util.{Success, Try}

trait ExceptionDirectives extends Logging {
  this: Directives with RespondDirectives =>

  private def handleSQSException(e: SQSException, protocol: AWSProtocol): Route = {
    protocol match {
      case AWSProtocol.AWSQueryProtocol =>
        respondWith(e.httpStatusCode) {
          e.toXml(EmptyRequestId)
        }
      case _ =>
        respondWithHeader(`X-Amzn-Query-Error`(e.errorType)) {
          complete(e.httpStatusCode, ErrorResponse(e.errorType, e.message))
        }
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

  final class `X-Amzn-Query-Error`(val value: String) extends ModeledCustomHeader[`X-Amzn-Query-Error`] {
    override def renderInRequests: Boolean = false
    override def renderInResponses: Boolean = true
    override def companion: ModeledCustomHeaderCompanion[`X-Amzn-Query-Error`] = `X-Amzn-Query-Error`
  }

  object `X-Amzn-Query-Error` extends ModeledCustomHeaderCompanion[`X-Amzn-Query-Error`] {
    override val name: String = "X-Amzn-Query-Error"

    override def parse(value: String): Try[`X-Amzn-Query-Error`] =
      Success(new `X-Amzn-Query-Error`(value))
  }
}
