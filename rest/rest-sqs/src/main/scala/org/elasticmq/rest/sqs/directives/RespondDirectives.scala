package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.{Directives, RequestContext, Route}
import org.elasticmq.rest.sqs.Constants._
import scala.language.postfixOps

import scala.xml.{Elem, Null, UnprefixedAttribute}

trait RespondDirectives {
  this: Directives =>

  def respondWith(elem: Elem): Route =
    namespace { ns => (ctx: RequestContext) =>
      {
        val result = elem % ns
        ctx.complete(HttpEntity(`text/xml(UTF-8)`, result.toString()))
      }
    }

  def respondWith(statusCode: Int)(elem: Elem): Route =
    namespace { ns => (ctx: RequestContext) =>
      {
        val result = elem % ns
        ctx.complete((statusCode, HttpEntity(`text/xml(UTF-8)`, result.toString())))
      }
    }

  private def namespace(route: UnprefixedAttribute => Route): Route =
    parameter("Version" ?) { versionOpt =>
      val version = versionOpt match {
        case Some(v) if !v.isEmpty => v
        case _                     => SqsDefaultVersion
      }

      route(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null))
    }
}
