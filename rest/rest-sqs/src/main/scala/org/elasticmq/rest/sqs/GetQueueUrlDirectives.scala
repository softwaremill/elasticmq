package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def getQueueUrl(p: AnyParams) = {
    p.action("GetQueueUrl") {
      rootPath {
        queueDataFromParams(p) { queueData =>
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