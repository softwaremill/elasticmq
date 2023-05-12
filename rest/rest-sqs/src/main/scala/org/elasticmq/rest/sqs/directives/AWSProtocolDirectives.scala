package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.{ContentType, MediaType}
import akka.http.scaladsl.server.Directives.extractRequest
import org.elasticmq.rest.sqs.AWSProtocol

trait AWSProtocolDirectives {

  import AWSProtocolDirectives._
  def extractProtocol = extractRequest.map { request =>
    request.entity.contentType match {
      case contentType if contentType.mediaType == `AWSJsonProtocol1.0ContentType`.mediaType =>
        AWSProtocol.`AWSJsonProtocol1.0`
      case _ => AWSProtocol.AWSQueryProtocol
    }
  }

}

object AWSProtocolDirectives {
  val `AWSJsonProtocol1.0ContentType` =
    ContentType.WithMissingCharset(MediaType.customWithOpenCharset("application", "x-amz-json-1.0"))
}
