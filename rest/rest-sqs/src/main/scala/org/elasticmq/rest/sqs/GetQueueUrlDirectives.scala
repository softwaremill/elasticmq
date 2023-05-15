package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.Action.GetQueueUrl
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def getQueueUrl(p: AnyParams, protocol: AWSProtocol) = {
    p.action(GetQueueUrl) {
      rootPath {
        queueDataFromParams(p) { queueData =>
          queueURL(queueData.name) { url =>
            protocol match {
              case AWSProtocol.`AWSJsonProtocol1.0` =>
                complete(GetQueueURLResponse(url))
              case _ =>
                respondWith {
                  <GetQueueUrlResponse>
                    <GetQueueUrlResult>
                      <QueueUrl>{url}</QueueUrl>
                    </GetQueueUrlResult>
                    <ResponseMetadata>
                      <RequestId>{EmptyRequestId}</RequestId>
                    </ResponseMetadata>
                  </GetQueueUrlResponse>
                }
            }
          }
        }
      }
    }
  }
}

case class GetQueueURLResponse(QueueUrl: String)

object GetQueueURLResponse {
  implicit val format: RootJsonFormat[GetQueueURLResponse] = jsonFormat1(GetQueueURLResponse.apply)
}
