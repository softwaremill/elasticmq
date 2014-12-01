package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.ClearQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait NonSQSDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val nonSQSDirectives = {
    queueActorFromRequest { queueActor =>
      action("Clear") {
        for {
          _ <- queueActor ? ClearQueue()
        } yield  {
          respondWith {
            <ClearQueueResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </ClearQueueResponse>
          }
        }
      }
    }
  }
}
