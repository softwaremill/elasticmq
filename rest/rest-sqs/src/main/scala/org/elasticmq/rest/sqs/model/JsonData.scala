package org.elasticmq.rest.sqs.model

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.{ContentTypeRange, HttpEntity, RequestEntity}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.ByteString
import org.elasticmq.rest.sqs.directives.AWSProtocolDirectives

import scala.concurrent.Future
import spray.json._
import spray.json.DefaultJsonProtocol._

case class JsonData(params: Map[String, String])

object JsonData {
  implicit val AmzJsonUnmarshaller: FromEntityUnmarshaller[JsonData] =
    Unmarshaller.withMaterializer[HttpEntity, JsonData] { implicit ec => implicit materializer => entity =>
      entity.contentType match {
        case contentType if contentType.mediaType == AWSProtocolDirectives.`AWSJsonProtocol1.0ContentType`.mediaType =>
          entity.dataBytes
            .runFold(ByteString.empty)(_ ++ _)
            .map(bs => JsonData(bs.utf8String.parseJson.convertTo[Map[String, String]]))
        case _ =>
          Future.failed(
            Unmarshaller.UnsupportedContentTypeException(
              entity.contentType,
              ContentTypeRange(AWSProtocolDirectives.`AWSJsonProtocol1.0ContentType`)
            )
          )
      }
    }

  implicit val AmzJsonMarshaller =
    Marshaller.withFixedContentType[JsonData, RequestEntity](AWSProtocolDirectives.`AWSJsonProtocol1.0ContentType`) {
      data =>
        HttpEntity(AWSProtocolDirectives.`AWSJsonProtocol1.0ContentType`, ByteString(data.params.toJson.prettyPrint))
    }
}
