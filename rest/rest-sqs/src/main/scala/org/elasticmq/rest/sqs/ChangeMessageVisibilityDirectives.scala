package org.elasticmq.rest.sqs

import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._
import org.elasticmq.msg.UpdateVisibilityTimeout
import org.elasticmq.rest.sqs.Action.ChangeMessageVisibility
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.elasticmq.rest.sqs.model.RequestPayload

trait ChangeMessageVisibilityDirectives { this: ElasticMQDirectives with ResponseMarshaller =>
  def changeMessageVisibility(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(ChangeMessageVisibility) {
      val requestParams = p.as[ChangeMessageVisibilityActionRequest]

      queueActorFromUrl(requestParams.QueueUrl) { queueActor =>
        val result = queueActor ? UpdateVisibilityTimeout(
          DeliveryReceipt(requestParams.ReceiptHandle),
          MillisVisibilityTimeout.fromSeconds(requestParams.VisibilityTimeout)
        )
        result.map {
          case Left(error) => throw new SQSException(error.code, errorMessage = Some(error.message))
          case Right(_) =>
            emptyResponse("ChangeMessageVisibilityResponse")
        }
      }
    }
  }

  case class ChangeMessageVisibilityActionRequest(QueueUrl: String, ReceiptHandle: String, VisibilityTimeout: Int)

  object ChangeMessageVisibilityActionRequest {
    implicit val requestJsonFormat: RootJsonFormat[ChangeMessageVisibilityActionRequest] = jsonFormat3(
      ChangeMessageVisibilityActionRequest.apply
    )

    implicit val requestParamReader: FlatParamsReader[ChangeMessageVisibilityActionRequest] =
      new FlatParamsReader[ChangeMessageVisibilityActionRequest] {
        override def read(params: Map[String, String]): ChangeMessageVisibilityActionRequest = {
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          val receiptHandle = requiredParameter(params)(ReceiptHandleParameter)
          val visibilityTimeout = requiredParameter(params)(VisibilityTimeoutParameter).toInt
          ChangeMessageVisibilityActionRequest(queueUrl, receiptHandle, visibilityTimeout)
        }
      }
  }

}
