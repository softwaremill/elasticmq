package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.ClearQueue
import org.elasticmq.rest.sqs.Action.PurgeQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol.{StringJsonFormat, jsonFormat1}
import spray.json.RootJsonFormat

trait PurgeQueueDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>
  def purgeQueue(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(PurgeQueue) {
      val requestParams = p.as[PurgeQueueActionRequest]

      queueActorFromUrl(requestParams.QueueUrl) { queueActor =>
        for {
          _ <- queueActor ? ClearQueue()
        } yield {
          emptyResponse("PurgeQueueResponse")
        }
      }
    }
  }

  case class PurgeQueueActionRequest(QueueUrl: String)

  object PurgeQueueActionRequest {
    implicit val requestJsonFormat: RootJsonFormat[PurgeQueueActionRequest] = jsonFormat1(PurgeQueueActionRequest.apply)

    implicit val requestParamReader: FlatParamsReader[PurgeQueueActionRequest] =
      new FlatParamsReader[PurgeQueueActionRequest] {
        override def read(params: Map[String, String]): PurgeQueueActionRequest = {
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          PurgeQueueActionRequest(queueUrl)
        }
      }
  }
}
