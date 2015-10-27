package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer

trait AnyParamDirectives {
  this: Directives =>

  def anyParamsMap(body: Map[String, String] => Route) = {
    parameterMap { queryParameters =>
      entity(as[FormData]) { fd =>
        body((fd.fields.toMap ++ queryParameters).filter(_._1 != ""))
      }
    }
  }

  implicit def materializer: Materializer
}
