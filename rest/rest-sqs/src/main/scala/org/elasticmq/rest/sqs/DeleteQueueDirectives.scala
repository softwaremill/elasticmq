package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._

import scala.async.Async._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.rest.sqs.Action.{DeleteQueue => DeleteQueueAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

trait DeleteQueueDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>
  def deleteQueue(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(DeleteQueueAction) {
      queueActorAndNameFromUrl(p.as[DeleteQueueActionRequest].QueueUrl) {
        (_, queueName) => // We need the queue actor just to check that the queue exists
          async {
            await(queueManagerActor ? DeleteQueue(queueName))
            emptyResponse("DeleteQueueResponse")
          }
      }
    }
  }
}

case class DeleteQueueActionRequest(QueueUrl: String)

object DeleteQueueActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[DeleteQueueActionRequest] = jsonFormat1(DeleteQueueActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[DeleteQueueActionRequest] =
    new FlatParamsReader[DeleteQueueActionRequest] {
      override def read(params: Map[String, String]): DeleteQueueActionRequest = {
        new DeleteQueueActionRequest(
          requiredParameter(params)(QueueUrlParameter)
        )
      }
    }
}
