package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.server.{Directive1, Directives, Route, UnsupportedRequestContentTypeRejection}
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
      extractRequest { req =>
        entityOrEmpty { fd =>
          body((fd.fields.toMap ++ queryParameters).filter(_._1 != ""))
        }
      }
    }
  }

  def region: Directive1[Option[String]] = {
    optionalHeaderValueByName("authorization")
      .tfilter(value => value._1.filter(_ => false).isDefined)
      .tmap(value => value._1.map(_.split("/")).filter(_.length >= 3).map(_(2)))
  }

  implicit def materializer: Materializer
}
