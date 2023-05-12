package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.{FormData, HttpRequest}
import akka.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import akka.stream.Materializer
import org.elasticmq.rest.sqs.AWSProtocol
import org.elasticmq.rest.sqs.model.JsonData

trait AnyParamDirectives {
  this: Directives =>

  private def formDataOrEmpty =
    entity(as[FormData]).recoverPF {
      // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
      case Seq(UnsupportedRequestContentTypeRejection(_)) =>
        provide(FormData.Empty)
    }

  private def extractActionFromHeader(request: HttpRequest) =
    request.headers
      .find(_.name() == "X-Amz-Target")
      .map { header =>
        header.value() match {
          case s"AmazonSQS.$action" => Map("Action" -> action)
          case _                    => Map.empty[String, String]
        }
      }
      .getOrElse(Map.empty)
  private def extractAwsXRayTracingHeader(request: HttpRequest): Map[String, String] = {
    request.headers
      .find(_.name().equalsIgnoreCase("X-Amzn-Trace-Id"))
      .map(header => header.name() -> header.value())
      .toMap
  }

  def anyParamsMap(protocol: AWSProtocol)(body: Map[String, String] => Route) = {

    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` =>
        extractRequest { request =>
          entity(as[JsonData]) { json =>
            body(
              extractActionFromHeader(request) ++ json.params ++ extractAwsXRayTracingHeader(request)
            )
          }
        }
      case _ =>
        parameterMap { queryParameters =>
          extractRequest { request =>
            formDataOrEmpty { fd =>
              body(
                (fd.fields.toMap ++ queryParameters ++ extractAwsXRayTracingHeader(request))
                  .filter(_._1 != "")
              )
            }
          }
        }
    }
  }

  implicit def materializer: Materializer
}
