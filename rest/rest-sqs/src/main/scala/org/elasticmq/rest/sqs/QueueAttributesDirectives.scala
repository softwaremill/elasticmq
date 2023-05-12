package org.elasticmq.rest.sqs

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Route
import org.elasticmq.rest.sqs.Action.{GetQueueAttributes, SetQueueAttributes}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future

trait QueueAttributesDirectives {
  this: ElasticMQDirectives with QueueAttributesOps =>

  def getQueueAttributes(p: RequestPayload, protocol: AWSProtocol): Route = {
    p.action(GetQueueAttributes) {
      val requestParams = p.as[GetQueueAttributesActionRequest]
      queueActorAndDataFromQueueUrl(requestParams.QueueUrl) { (queueActor, queueData) =>
        val attributesFuture: Future[List[(String, String)]] =
          getQueueAttributes(requestParams.AttributeNames.getOrElse(List.empty), queueActor, queueData)

        def responseXml(attributes: List[(String, String)]) = {
          <GetQueueAttributesResponse>
            <GetQueueAttributesResult>
              {attributesToXmlConverter.convert(attributes)}
            </GetQueueAttributesResult>
            <ResponseMetadata>
              <RequestId>{EmptyRequestId}</RequestId>
            </ResponseMetadata>
          </GetQueueAttributesResponse>
        }

        attributesFuture.map { attributes =>
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                responseXml(attributes)
              }
            case _ => {
              complete(GetQueueAttributesResponse(attributes.toMap))
            }
          }
        }
      }
    }
  }

  case class GetQueueAttributesActionRequest(AttributeNames: Option[List[String]], QueueUrl: String)

  object GetQueueAttributesActionRequest {
    implicit val responseJsonFormat: RootJsonFormat[GetQueueAttributesActionRequest] = jsonFormat2(
      GetQueueAttributesActionRequest.apply
    )

    implicit val requestParamReader: FlatParamsReader[GetQueueAttributesActionRequest] =
      new FlatParamsReader[GetQueueAttributesActionRequest] {
        override def read(params: Map[String, String]): GetQueueAttributesActionRequest = {
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          val attributeNames =
            AttributesModule.attributeNamesReader.read(params, QueueReadableAttributeNames.AllAttributeNames)
          GetQueueAttributesActionRequest(Some(attributeNames), queueUrl)
        }
      }
  }

  case class GetQueueAttributesResponse(Attributes: Map[String, String])

  object GetQueueAttributesResponse {
    implicit val responseJsonFormat: RootJsonFormat[GetQueueAttributesResponse] = jsonFormat1(
      GetQueueAttributesResponse.apply
    )
  }

  def setQueueAttributes(p: RequestPayload, protocol: AWSProtocol): Route = {
    p.action(SetQueueAttributes) {
      val requestParameters = p.as[SetQueueAttributesActionRequest]
      queueActorFromUrl(requestParameters.QueueUrl) { queueActor =>
        val result = setQueueAttributes(requestParameters.Attributes, queueActor, queueManagerActor)
        Future.sequence(result).map { _ =>
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                <SetQueueAttributesResponse>
                  <ResponseMetadata>
                    <RequestId>{EmptyRequestId}</RequestId>
                  </ResponseMetadata>
                </SetQueueAttributesResponse>
              }
            case _ => complete(status = 200, HttpEntity.Empty)
          }
        }
      }
    }
  }

  case class SetQueueAttributesActionRequest(Attributes: Map[String, String], QueueUrl: String)

  object SetQueueAttributesActionRequest {
    implicit val jsonRequestFormat: RootJsonFormat[SetQueueAttributesActionRequest] = jsonFormat2(
      SetQueueAttributesActionRequest.apply
    )

    implicit val requestParamReader: FlatParamsReader[SetQueueAttributesActionRequest] =
      new FlatParamsReader[SetQueueAttributesActionRequest] {
        override def read(params: Map[String, String]): SetQueueAttributesActionRequest = {
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          val attributes = AttributesModule.attributeNameAndValuesReader.read(params)
          SetQueueAttributesActionRequest(attributes, queueUrl)
        }
      }
  }
}
