package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import akka.stream.Materializer

trait AnyParamDirectives {
  this: Directives =>

  private def entityOrEmpty = entity(as[FormData]).recoverPF {
    // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
    case Seq(UnsupportedRequestContentTypeRejection(_)) =>
      provide(FormData.Empty)
  }

  def anyParamsMap(body: Map[String, String] => Route) = {
    parameterMap { queryParameters =>
      extractRequest { _ =>
        entityOrEmpty { fd =>
          body((fd.fields.toMap ++ queryParameters).filter(_._1 != ""))
        }
      }
    }
  }

  implicit def materializer: Materializer
}
