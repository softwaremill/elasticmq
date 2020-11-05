package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.ClearQueue
import org.elasticmq.rest.sqs.Action.PurgeQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait PurgeQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def purgeQueue(p: AnyParams) = {
    p.action(PurgeQueue) {
      queueActorFromRequest(p) { queueActor =>
        for {
          _ <- queueActor ? ClearQueue()
        } yield {
          respondWith {
            <PurgeQueueResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </PurgeQueueResponse>
          }
        }
      }
    }
  }
}
