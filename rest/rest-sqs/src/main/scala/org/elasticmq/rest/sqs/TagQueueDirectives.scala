package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait TagQueueDirectives {
  this: ElasticMQDirectives with TagsModule =>

  def listQueueTags(p: AnyParams) = {
    p.action("ListQueueTags") {
      queueActorAndDataFromRequest(p) { (_, queueData) =>
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

  def untagQueue(p: AnyParams) = {
    p.action("UntagQueue") {
      queueActorFromRequest(p) { queueActor =>
        val tags = tagNamesReader.read(p)
        queueActor ? RemoveQueueTags(tags)
        respondWith {
          <UntagQueueResponse>
            <ResponseMetadata>
              <RequestId>{EmptyRequestId}</RequestId>
            </ResponseMetadata>
          </UntagQueueResponse>
        }
      }
    }
  }

  def tagQueue(p: AnyParams) = {
    p.action("TagQueue") {
      queueActorFromRequest(p) { queueActor =>
        val tags = tagNameAndValuesReader.read(p)
        queueActor ? UpdateQueueTags(tags)
        respondWith(
          <TagQueueResponse>
            <ResponseMetadata>
              <RequestId>{EmptyRequestId}</RequestId>
            </ResponseMetadata>
          </TagQueueResponse>
        )
      }
    }
  }
}
