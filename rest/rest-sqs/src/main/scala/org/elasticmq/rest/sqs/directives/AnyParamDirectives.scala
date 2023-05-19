package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.{FormData, HttpRequest}
import akka.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import akka.stream.Materializer
import org.elasticmq.rest.sqs.AWSProtocol
import org.elasticmq.rest.sqs.model.{JsonData, RequestPayload}

trait AnyParamDirectives {
  this: Directives =>

  private def formDataOrEmpty =
    entity(as[FormData]).recoverPF {
      // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
      case Seq(UnsupportedRequestContentTypeRejection(_)) =>
        provide(FormData.Empty)
    }

  private val actionHeaderPattern = """^AmazonSQS\.(\w+)$""".r
  private def extractActionFromHeader(request: HttpRequest) =
    request.headers
      .find(_.name() == "X-Amz-Target")
      .flatMap(
        _.value() match {
          case actionHeaderPattern(action) => Some(action)
          case _                           => None
        }
      ).getOrElse(throw new IllegalArgumentException("Couldn't find header X-Amz-Target"))
  private def extractAwsXRayTracingHeader(request: HttpRequest): Option[String] = {
    request.headers
      .find(_.name().equalsIgnoreCase("X-Amzn-Trace-Id"))
      .map(_.value())
  }

  def anyParamsMap(protocol: AWSProtocol)(body: RequestPayload => Route) = {

    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` =>
        extractRequest { request =>
          entity(as[JsonData]) { json =>
            body(
              RequestPayload.JsonParams(
                json.payload,
                extractActionFromHeader(request),
                extractAwsXRayTracingHeader(request)
              )
            )
          }
        }
      case _ =>
        parameterMap { queryParameters =>
          extractRequest { request =>
            formDataOrEmpty { fd =>
              val params = queryParameters ++ fd.fields.toMap

              body(
                RequestPayload.QueryParams(
                  params.filter(_._1 != ""),
                  extractAwsXRayTracingHeader(request)
                )
              )
            }
          }
        }
    }
  }

  implicit def materializer: Materializer
}
