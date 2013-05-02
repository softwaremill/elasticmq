package org.elasticmq.rest.sqs

import Constants._

trait DeleteQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val deleteQueue = {
    action("DeleteQueue") {
      queuePath { queue =>
        queue.delete()

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