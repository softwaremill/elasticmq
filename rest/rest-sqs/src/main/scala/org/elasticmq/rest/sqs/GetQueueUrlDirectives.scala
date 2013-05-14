package org.elasticmq.rest.sqs

import Constants._

trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val getQueueUrl = {
    action("GetQueueUrl") {
      rootPath {
        queueDataFromPath { queueData =>
          respondWith {
            <GetQueueUrlResponse>
              <GetQueueUrlResult>
                <QueueUrl>{queueURL(queueData)}</QueueUrl>
              </GetQueueUrlResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </GetQueueUrlResponse>
          }
        }
      }
    }
  }
}