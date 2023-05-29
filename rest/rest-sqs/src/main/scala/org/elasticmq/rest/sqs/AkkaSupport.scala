package org.elasticmq.rest.sqs

import akka.http.javadsl.server.RouteResult
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, RequestEntity}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.RequestContext
import akka.util.ByteString
import org.elasticmq.rest.sqs.Constants.EmptyRequestId
import org.elasticmq.rest.sqs.directives.RespondDirectives

import scala.concurrent.Future
import scala.xml._
import scala.xml.{Null, UnprefixedAttribute}

trait AkkaSupport {
  _: RespondDirectives =>

  private def namespace[T](body: UnprefixedAttribute => Marshaller[T, RequestEntity])(implicit v: XmlNsVersion): Marshaller[T, RequestEntity] = {
    body(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(v.version), Null))
  }

  implicit def elasticMQMarshaller[T](implicit xmlSerializer: XmlSerializer[T], json: RootJsonFormat[T], protocol: AWSProtocol, version: XmlNsVersion) =
    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` => sprayJsonMarshaller[T]
      case _ =>
        namespace { ns =>
          Marshaller.withFixedContentType[T, RequestEntity](`text/xml(UTF-8)`) { t =>
            val xml = xmlSerializer.toXml(t) % ns
            HttpEntity(`text/xml(UTF-8)`, ByteString(xml.toString()))
          }
        }
    }

  def emptyResponse(xmlTagName: String)(implicit protocol: AWSProtocol) = {
    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` =>complete(200, HttpEntity.Empty)
      case _ =>
        respondWith {
          <wrapper>
            <ResponseMetadata>
              <RequestId>{EmptyRequestId}</RequestId>
            </ResponseMetadata>
          </wrapper> % Attribute(None, "name", Text(xmlTagName), Null)
        }
    }
  }
}
