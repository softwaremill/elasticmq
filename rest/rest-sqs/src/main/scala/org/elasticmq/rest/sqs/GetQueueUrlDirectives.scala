package org.elasticmq.rest.sqs

import Constants._

trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val getQueueUrl = {
    action("GetQueueUrl") {
      rootPath {
        anyParam("QueueName") { queueName =>
          val queueOption = client.lookupQueue(queueName)

          queueOption match {
            case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
            case Some(queue) => {
              respondWith {
                <GetQueueUrlResponse>
                  <GetQueueUrlResult>
                    <QueueUrl>{queueURL(queue)}</QueueUrl>
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
  }
}