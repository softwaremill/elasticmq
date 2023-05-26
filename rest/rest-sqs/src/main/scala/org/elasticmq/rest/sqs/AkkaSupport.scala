package org.elasticmq.rest.sqs

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, RequestEntity}
import akka.util.ByteString
import org.elasticmq.rest.sqs.Constants.SqsDefaultVersion

import scala.xml.{Null, UnprefixedAttribute}

trait AkkaSupport {

  private def namespace[T](body: UnprefixedAttribute => Marshaller[T, RequestEntity]): Marshaller[T, RequestEntity] = {
    val version = SqsDefaultVersion
    body(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null))
  }

  implicit def elasticMQMarshaller[T] (implicit xmlSerializer: XmlSerializer[T], json: RootJsonFormat[T], protocol: AWSProtocol) =
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

}
