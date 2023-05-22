package org.elasticmq.rest.sqs

import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ClearQueue
import org.elasticmq.rest.sqs.Action.PurgeQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol.{StringJsonFormat, jsonFormat1}
import spray.json.RootJsonFormat

trait PurgeQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def purgeQueue(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(PurgeQueue) {
      val requestParams = p.as[PurgeQueueActionRequest]

      queueActorFromUrl(requestParams.QueueUrl){ queueActor =>
        for {
          _ <- queueActor ? ClearQueue()
        } yield {
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                <PurgeQueueResponse>
                  <ResponseMetadata>
                    <RequestId>
                      {EmptyRequestId}
                    </RequestId>
                  </ResponseMetadata>
                </PurgeQueueResponse>
              }
            case _ => complete(status = 200, HttpEntity.Empty)
          }
        }
      }
    }
  }

  case class PurgeQueueActionRequest(QueueUrl: String)

  object PurgeQueueActionRequest {
    implicit val requestJsonFormat: RootJsonFormat[PurgeQueueActionRequest] = jsonFormat1(PurgeQueueActionRequest.apply)

    implicit val requestParamReader: FlatParamsReader[PurgeQueueActionRequest] = new FlatParamsReader[PurgeQueueActionRequest] {
      override def read(params: Map[String, String]): PurgeQueueActionRequest = {
        val queueUrl = requiredParameter(params)("QueueUrl")
        PurgeQueueActionRequest(queueUrl)
      }
    }
  }
}
