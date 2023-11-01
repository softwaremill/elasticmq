package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.model.ContentTypes._
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.server.{Directives, RequestContext, Route}
import org.elasticmq.rest.sqs.Constants._

import scala.language.postfixOps
import org.apache.pekko.http.scaladsl.server
import org.elasticmq.rest.sqs.{AWSProtocol, MarshallerDependencies}
import org.elasticmq.rest.sqs.Constants.EmptyRequestId

import scala.xml._
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
        case Some(v) if v.nonEmpty => v
        case _                     => SqsDefaultVersion
      }

      route(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null))
    }

  def emptyResponse(xmlTagName: String)(implicit marshallerDependencies: MarshallerDependencies): server.Route = {
    marshallerDependencies.protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` => complete(200, HttpEntity.Empty)
      case _ =>
        respondWith {
          <wrapper>
            <ResponseMetadata>
              <RequestId>
                {EmptyRequestId}
              </RequestId>
            </ResponseMetadata>
          </wrapper> % Attribute(None, "name", Text(xmlTagName), Null)
        }
    }
  }
}
