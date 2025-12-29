package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.server.{Directive0, Directives}
import org.elasticmq.rest.sqs.{AWSCredentialsModule, AWSProtocol, SQSException}

trait AWSCredentialDirectives extends Directives {
  this: AWSCredentialsModule with ElasticMQDirectives =>

  private val accessKeyRegex = "Credential=([^/]+)/".r

  def verifyAWSAccessKeyId(protocol: AWSProtocol): Directive0 = {
    if (awsCredentials.accessKey.nonEmpty) {
      // Optional header in case it's missing
      optionalHeaderValueByName("Authorization").flatMap {
        case Some(authHeader) =>
          accessKeyRegex.findFirstMatchIn(authHeader) match {
            case Some(m) if m.group(1) == awsCredentials.accessKey =>
              pass
            case _ =>
              // Must return a Directive0 here
              complete(
                SQSException.invalidClientTokenId(
                  "The security token included in the request is invalid."
                )
              )
          }
        case None =>
          complete(
            SQSException.invalidClientTokenId(
              "The security token included in the request is invalid."
            )
          )
      }
    } else {
      pass
    }
  }
}
