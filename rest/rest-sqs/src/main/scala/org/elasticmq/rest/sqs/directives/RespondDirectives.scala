package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Route, Directives, RequestContext}

import scala.xml.{Null, UnprefixedAttribute, Elem}
import org.elasticmq.rest.sqs.Constants._

trait RespondDirectives {
  this: Directives =>

  def respondWith(elem: Elem): Route = namespace { ns =>
    (ctx: RequestContext) => {
      val result = elem % ns
      ctx.complete(result.toString())
    }
  }

  def respondWith(statusCode: Int)(elem: Elem): Route = namespace { ns =>
    (ctx: RequestContext) => {
      val result = elem % ns
      ctx.complete(statusCode, result.toString())
    }
  }

  private def namespace(route: UnprefixedAttribute => Route): Route = parameter("Version"?) { versionOpt =>
    val version = versionOpt match {
      case Some(v) if !v.isEmpty => v
      case _ => SqsDefaultVersion
    }

    route(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null))
  }
}
