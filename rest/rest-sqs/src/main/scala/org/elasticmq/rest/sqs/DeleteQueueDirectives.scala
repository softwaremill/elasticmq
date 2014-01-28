package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._
import akka.dataflow._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait DeleteQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val deleteQueue = {
    action("DeleteQueue") {
      queueActorAndNameFromPathOrParams { (queueActor, queueName) => // We need the queue actor just to check that the queue exists
        flow {
          (queueManagerActor ? DeleteQueue(queueName)).apply()

          respondWith {
            <DeleteQueueResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </DeleteQueueResponse>
          }
        }
      }
    }
  }
}