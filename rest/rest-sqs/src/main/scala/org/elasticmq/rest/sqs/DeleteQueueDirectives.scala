package org.elasticmq.rest.sqs

import Constants._
import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._

import scala.async.Async._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.rest.sqs.Action.{DeleteQueue => DeleteQueueAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

trait DeleteQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def deleteQueue(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(DeleteQueueAction) {
      queueActorAndNameFromUrl(p.as[DeleteQueueActionRequest].QueueUrl) {
        (_, queueName) => // We need the queue actor just to check that the queue exists
          async {
            await(queueManagerActor ? DeleteQueue(queueName))
            protocol match {
              case AWSProtocol.AWSQueryProtocol =>
                respondWith {
                  <DeleteQueueResponse>
                    <ResponseMetadata>
                      <RequestId>
                        {EmptyRequestId}
                      </RequestId>
                    </ResponseMetadata>
                  </DeleteQueueResponse>
                }
              case _ => complete(status = 200, HttpEntity.Empty)
            }
          }
      }
    }
  }
}

case class DeleteQueueActionRequest(QueueUrl: String)

object DeleteQueueActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[DeleteQueueActionRequest] = jsonFormat1(DeleteQueueActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[DeleteQueueActionRequest] = new FlatParamsReader[DeleteQueueActionRequest] {
    override def read(params: Map[String, String]): DeleteQueueActionRequest = {
      new DeleteQueueActionRequest(
        requiredParameter(params)("QueueUrl")
      )
    }
  }
}