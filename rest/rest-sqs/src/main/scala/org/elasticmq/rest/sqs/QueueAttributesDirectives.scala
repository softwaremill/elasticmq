package org.elasticmq.rest.sqs

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Route
import org.elasticmq.rest.sqs.Action.{GetQueueAttributes, SetQueueAttributes}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future

trait QueueAttributesDirectives {
  this: ElasticMQDirectives with QueueAttributesOps =>

  def getQueueAttributes(p: AnyParams, protocol: AWSProtocol): Route = {
    p.action(GetQueueAttributes) {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        val attributesFuture = getQueueAttributes(p, queueActor, queueData)

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
              println(attributes)
              complete(200, HttpEntity.Empty)
            }
          }
        }
      }
    }
  }

  case class GetQueueAttributesResponse(Attributes: List[String])

  object GetQueueAttributesResponse {
    implicit val format: RootJsonFormat[GetQueueAttributesResponse] = jsonFormat1(GetQueueAttributesResponse.apply)
  }

  def setQueueAttributes(p: AnyParams, protocol: AWSProtocol): Route = {
    p.action(SetQueueAttributes) {
      queueActorFromRequest(p) { queueActor =>
        val result = setQueueAttributes(p, queueActor, queueManagerActor)

        Future.sequence(result).map { _ =>
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                <SetQueueAttributesResponse>
                  <ResponseMetadata>
                    <RequestId>
                      {EmptyRequestId}
                    </RequestId>
                  </ResponseMetadata>
                </SetQueueAttributesResponse>
              }
            case _ => complete(status = 200, HttpEntity.Empty)
          }
        }
      }
    }
  }
}
