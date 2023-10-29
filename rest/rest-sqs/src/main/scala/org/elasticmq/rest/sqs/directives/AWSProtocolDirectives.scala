package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.model.{ContentType, MediaType}
import org.apache.pekko.http.scaladsl.server.Directives.extractRequest
import org.elasticmq.rest.sqs.AWSProtocol

trait AWSProtocolDirectives {
  def extractProtocol = extractRequest.map { request =>
    request.entity.contentType match {
      case AmzJsonProtocol(_) => AWSProtocol.`AWSJsonProtocol1.0`
      case _                  => AWSProtocol.AWSQueryProtocol
    }
  }

}

object AmzJsonProtocol {
  def unapply(contentType: ContentType): Option[MediaType] = {
    if (contentType.mediaType.subType.startsWith("x-amz-json")) {
      Some(contentType.mediaType)
    } else {
      None
    }
  }

  def contentType(version: String): ContentType.WithMissingCharset =
    ContentType.WithMissingCharset(MediaType.customWithOpenCharset("application", s"x-amz-json-$version"))
}
