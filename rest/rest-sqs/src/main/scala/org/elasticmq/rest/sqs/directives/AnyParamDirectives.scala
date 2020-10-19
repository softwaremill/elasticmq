package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.{FormData, HttpRequest}
import akka.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import akka.stream.Materializer

trait AnyParamDirectives {
  this: Directives =>

  private def entityOrEmpty =
    entity(as[FormData]).recoverPF {
      // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
      case Seq(UnsupportedRequestContentTypeRejection(_)) =>
        provide(FormData.Empty)
    }

  private def extractAwsXRayTracingHeader(request: HttpRequest): Map[String, String] = {
    request.headers.find(_.name() == "X-Amzn-Trace-Id").map(header => header.name() -> header.value()).toMap
  }

  def anyParamsMap(body: Map[String, String] => Route) = {
    parameterMap { queryParameters =>
      extractRequest { request =>
        entityOrEmpty { fd => body((fd.fields.toMap ++ queryParameters ++ extractAwsXRayTracingHeader(request)).filter(_._1 != "")) }
      }
    }
  }

  implicit def materializer: Materializer
}
