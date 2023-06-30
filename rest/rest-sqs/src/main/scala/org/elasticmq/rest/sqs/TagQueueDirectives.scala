package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.Action.{ListQueueTags, TagQueue, UntagQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.TagsModule.tagsToXmlConverter
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import org.elasticmq.rest.sqs.model.RequestPayload

import scala.xml.Elem

trait TagQueueDirectives {
  this: ElasticMQDirectives with TagsModule with ResponseMarshaller =>

  def listQueueTags(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(ListQueueTags) {
      val queueUrl = p.as[ListQueueTagsActionRequest].QueueUrl
      queueActorAndDataFromQueueUrl(queueUrl) { (_, queueData) =>
        complete(ListQueueTagsResponse(queueData.tags))
      }
    }
  }

  def untagQueue(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(UntagQueue) {
      val params = p.as[UntagQueueActionRequest]
      queueActorFromUrl(params.QueueUrl) { queueActor =>
        val tags = params.TagKeys
        queueActor ? RemoveQueueTags(tags)
        emptyResponse("UntagQueueResponse")
      }
    }
  }

  def tagQueue(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(TagQueue) {
      val params = p.as[TagQueueActionRequest]
      queueActorFromUrl(params.QueueUrl) { queueActor =>
        val tags = params.Tags
        queueActor ? UpdateQueueTags(tags)
        emptyResponse("TagQueueResponse")
      }
    }
  }
}

case class TagQueueActionRequest(
    QueueUrl: String,
    Tags: Map[String, String]
)

object TagQueueActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[TagQueueActionRequest] = jsonFormat2(TagQueueActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[TagQueueActionRequest] =
    new FlatParamsReader[TagQueueActionRequest] {
      override def read(params: Map[String, String]): TagQueueActionRequest = {
        val tags = TagsModule.tagNameAndValuesReader.read(params)
        val queueUrl = requiredParameter(params)(QueueUrlParameter)
        TagQueueActionRequest(queueUrl, tags)
      }
    }
}

case class UntagQueueActionRequest(
    QueueUrl: String,
    TagKeys: List[String]
)

object UntagQueueActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[UntagQueueActionRequest] = jsonFormat2(UntagQueueActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[UntagQueueActionRequest] =
    new FlatParamsReader[UntagQueueActionRequest] {
      override def read(params: Map[String, String]): UntagQueueActionRequest = {
        val tags = TagsModule.tagNamesReader.read(params)
        val queueUrl = requiredParameter(params)(QueueUrlParameter)
        UntagQueueActionRequest(queueUrl, tags)
      }
    }
}

case class ListQueueTagsActionRequest(
    QueueUrl: String
)

object ListQueueTagsActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[ListQueueTagsActionRequest] = jsonFormat1(
    ListQueueTagsActionRequest.apply
  )

  implicit val requestParamReader: FlatParamsReader[ListQueueTagsActionRequest] =
    new FlatParamsReader[ListQueueTagsActionRequest] {
      override def read(params: Map[String, String]): ListQueueTagsActionRequest = ListQueueTagsActionRequest(
        requiredParameter(params)(QueueUrlParameter)
      )
    }
}

case class ListQueueTagsResponse(Tags: Map[String, String])

object ListQueueTagsResponse {
  implicit val format: RootJsonFormat[ListQueueTagsResponse] = jsonFormat1(ListQueueTagsResponse.apply)

  implicit val xmlSerializer: XmlSerializer[ListQueueTagsResponse] = new XmlSerializer[ListQueueTagsResponse] {
    override def toXml(t: ListQueueTagsResponse): Elem =
      <ListQueueTagsResponse>
        <ListQueueTagsResult>
          {tagsToXmlConverter.convert(t.Tags)}
        </ListQueueTagsResult>
        <ResponseMetadata>
          <RequestId>
            {EmptyRequestId}
          </RequestId>
        </ResponseMetadata>
      </ListQueueTagsResponse>
  }
}
