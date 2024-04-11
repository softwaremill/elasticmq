package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.actor.reply._
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.rest.sqs.Action.{DeleteMessage => DeleteMessageAction}
import org.elasticmq.rest.sqs.SQSException.ElasticMQErrorOps
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

trait DeleteMessageDirectives { this: ElasticMQDirectives with ResponseMarshaller =>
  def deleteMessage(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(DeleteMessageAction) {
      val requestParams = p.as[DeleteMessageActionRequest]

      queueActorFromUrl(requestParams.QueueUrl) { queueActor =>
        val result = queueActor ? DeleteMessage(DeliveryReceipt(requestParams.ReceiptHandle))
        result.map {
          case Left(error) => throw error.toSQSException
          case Right(_)    => emptyResponse("DeleteMessageResponse")
        }
      }
    }
  }

  case class DeleteMessageActionRequest(QueueUrl: String, ReceiptHandle: String)

  object DeleteMessageActionRequest {
    implicit val requestJsonFormat: RootJsonFormat[DeleteMessageActionRequest] = jsonFormat2(
      DeleteMessageActionRequest.apply
    )

    implicit val requestParamReader: FlatParamsReader[DeleteMessageActionRequest] =
      new FlatParamsReader[DeleteMessageActionRequest] {
        override def read(params: Map[String, String]): DeleteMessageActionRequest = {
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          val receiptHandle = requiredParameter(params)(ReceiptHandleParameter)
          DeleteMessageActionRequest(queueUrl, receiptHandle)
        }
      }
  }
}
