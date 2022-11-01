package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.Action.GetQueueUrl
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait GetQueueUrlDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def getQueueUrl(p: AnyParams) = {
    p.action(GetQueueUrl) {
      rootPath {
        queueDataFromParams(p) { queueData =>
          queueURL(queueData.name) { url =>
            respondWith {
              <GetQueueUrlResponse>
                <GetQueueUrlResult>
                  <QueueUrl>{url}</QueueUrl>
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
