package org.elasticmq.rest.sqs

import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.Action.{ListQueueTags, TagQueue, UntagQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

trait TagQueueDirectives {
  this: ElasticMQDirectives with TagsModule =>

  def listQueueTags(p: AnyParams, protocol: AWSProtocol) = {
    p.action(ListQueueTags) {
      queueActorAndDataFromRequest(p) { (_, queueData) =>
        protocol match {
          case AWSProtocol.`AWSJsonProtocol1.0` =>
            complete(ListQueueTagsResponse(queueData.tags))
          case _ =>
            respondWith {
              <ListQueueTagsResponse>
                <ListQueueTagsResult>
                  {tagsToXmlConverter.convert(queueData.tags)}
                </ListQueueTagsResult>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </ListQueueTagsResponse>
            }
        }
      }
    }
  }

  def untagQueue(p: AnyParams, protocol: AWSProtocol) = {
    p.action(UntagQueue) {
      queueActorFromRequest(p) { queueActor =>
        val tags = tagNamesReader.read(p)
        queueActor ? RemoveQueueTags(tags)
        protocol match {
          case AWSProtocol.`AWSJsonProtocol1.0` =>
            respondWith {
              <UntagQueueResponse>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </UntagQueueResponse>
            }
          case _ =>
            complete(HttpEntity.Empty)
        }

      }
    }
  }

  def tagQueue(p: AnyParams, protocol: AWSProtocol) = {
    p.action(TagQueue) {
      queueActorFromRequest(p) { queueActor =>
        val tags = tagNameAndValuesReader.read(p)
        queueActor ? UpdateQueueTags(tags)
        protocol match {
          case AWSProtocol.`AWSJsonProtocol1.0` =>
            respondWith(
              <TagQueueResponse>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </TagQueueResponse>
            )
          case _ =>
            complete(HttpEntity.Empty)
        }
      }
    }
  }
}

case class ListQueueTagsResponse(Tags: Map[String, String])

object ListQueueTagsResponse {
  implicit val format: RootJsonFormat[ListQueueTagsResponse] = jsonFormat1(ListQueueTagsResponse.apply)
}
