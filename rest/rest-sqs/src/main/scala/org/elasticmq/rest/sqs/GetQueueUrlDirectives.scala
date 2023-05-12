package org.elasticmq.rest.sqs

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.elasticmq.rest.sqs.Action.GetQueueUrl
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.GetQueueUrlRequest.{requestJsonFormat, requestParamReader}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import org.elasticmq.rest.sqs.model.RequestPayload

trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def getQueueUrl(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(GetQueueUrl) {
      rootPath {
        val requestParams = p.as[GetQueueUrlActionRequest]

        queueURL(requestParams.QueueName) { url =>
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

case class GetQueueUrlActionRequest(QueueName: String, QueueOwnerAWSAccountId: Option[String])

object GetQueueUrlRequest {
  implicit val requestJsonFormat: RootJsonFormat[GetQueueUrlActionRequest] = jsonFormat2(GetQueueUrlActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[GetQueueUrlActionRequest] =
    new FlatParamsReader[GetQueueUrlActionRequest] {
      override def read(params: Map[String, String]): GetQueueUrlActionRequest = {
        val queueName = requiredParameter(params)(QueueNameParameter)
        val queueOwnerAWSAccountId = optionalParameter(params)("QueueOwnerAWSAccountId")
        GetQueueUrlActionRequest(queueName, queueOwnerAWSAccountId)
      }
    }
}

case class GetQueueURLResponse(QueueUrl: String)

object GetQueueURLResponse {
  implicit val format: RootJsonFormat[GetQueueURLResponse] = jsonFormat1(GetQueueURLResponse.apply)
}
