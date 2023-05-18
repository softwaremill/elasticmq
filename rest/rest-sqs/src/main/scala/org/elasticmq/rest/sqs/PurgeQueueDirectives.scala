package org.elasticmq.rest.sqs

import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ClearQueue
import org.elasticmq.rest.sqs.Action.PurgeQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait PurgeQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def purgeQueue(p: AnyParams, protocol: AWSProtocol) = {
    p.action(PurgeQueue) {
      queueActorFromRequest(p) { queueActor =>
        for {
          _ <- queueActor ? ClearQueue()
        } yield {
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                <PurgeQueueResponse>
                  <ResponseMetadata>
                    <RequestId>
                      {EmptyRequestId}
                    </RequestId>
                  </ResponseMetadata>
                </PurgeQueueResponse>
              }
            case _ => complete(status = 200, HttpEntity.Empty)
          }
        }
      }
    }
  }
}
