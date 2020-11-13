package org.elasticmq.rest.sqs

import akka.http.scaladsl.server.Route
import org.elasticmq.rest.sqs.Action.{GetQueueAttributes, SetQueueAttributes}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

import scala.concurrent.Future

trait QueueAttributesDirectives {
  this: ElasticMQDirectives with QueueAttributesOps =>

  def getQueueAttributes(p: AnyParams): Route = {
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
          respondWith {
            responseXml(attributes)
          }
        }
      }
    }
  }

  def setQueueAttributes(p: AnyParams): Route = {
    p.action(SetQueueAttributes) {
      queueActorFromRequest(p) { queueActor =>
        val result = setQueueAttributes(p, queueActor, queueManagerActor)

        Future.sequence(result).map { _ =>
          respondWith {
            <SetQueueAttributesResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </SetQueueAttributesResponse>
          }
        }
      }
    }
  }
}
