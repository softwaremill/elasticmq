package org.elasticmq.rest.sqs

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.RootJsonFormat
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.ContentTypes._
import org.apache.pekko.http.scaladsl.model.{HttpEntity, RequestEntity}
import org.apache.pekko.util.ByteString
import org.elasticmq.rest.sqs.directives.RespondDirectives

import scala.xml.{Elem, Null, UnprefixedAttribute}

trait ResponseMarshaller { this: RespondDirectives =>

  private def namespace[T](
      body: UnprefixedAttribute => Marshaller[T, RequestEntity]
  )(implicit v: XmlNsVersion): Marshaller[T, RequestEntity] = {
    body(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(v.version), Null))
  }

  implicit def elasticMQMarshaller[T](implicit
      xmlSerializer: XmlSerializer[T],
      json: RootJsonFormat[T],
      marshallerDependencies: MarshallerDependencies
  ): Marshaller[T, RequestEntity] =
    marshallerDependencies.protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` => sprayJsonMarshaller[T]
      case _                                =>
        namespace { ns =>
          Marshaller.withFixedContentType[T, RequestEntity](`text/xml(UTF-8)`) { t =>
            val xml = xmlSerializer.toXml(t) % ns
            HttpEntity(`text/xml(UTF-8)`, ByteString(xml.toString()))
          }
        }(marshallerDependencies.xmlNsVersion)
    }
}

trait XmlSerializer[T] {

  def toXml(t: T): Elem

}

object XmlSerializer {
  def apply[T](implicit instance: XmlSerializer[T]) = instance
}
