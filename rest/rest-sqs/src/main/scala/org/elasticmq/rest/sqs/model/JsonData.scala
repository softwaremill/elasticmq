package org.elasticmq.rest.sqs.model

import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.{ContentTypeRange, HttpEntity, RequestEntity}
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.util.ByteString
import org.elasticmq.rest.sqs.directives.AmzJsonProtocol

import scala.concurrent.Future
import spray.json._
case class JsonData(payload: JsObject)

object JsonData {
  implicit val AmzJsonUnmarshaller: FromEntityUnmarshaller[JsonData] =
    Unmarshaller.withMaterializer[HttpEntity, JsonData] { implicit ec => implicit materializer => entity =>
      entity.contentType match {
        case AmzJsonProtocol(_) =>
          entity.dataBytes
            .runFold(ByteString.empty)(_ ++ _)
            .map(bs => JsonData(bs.utf8String.parseJson.asJsObject))
        case _ =>
          Future.failed(
            Unmarshaller.UnsupportedContentTypeException(
              entity.contentType,
              ContentTypeRange(entity.contentType)
            )
          )
      }
    }

  implicit val AmzJsonMarshaller =
    Marshaller.withFixedContentType[JsonData, RequestEntity](AmzJsonProtocol.contentType("1.0")) { data =>
      HttpEntity(AmzJsonProtocol.contentType("1.0"), ByteString(data.payload.prettyPrint))
    }
}
