package org.elasticmq.rest.sqs

import Constants._

trait ChangeMessageVisibilityBatchDirectives { this: ElasticMQDirectives with ChangeMessageVisibilityDirectives with BatchRequestsModule =>
  val changeMessageVisibilityBatch = {
    action("ChangeMessageVisibilityBatch") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          val results = batchRequest("ChangeMessageVisibilityBatchRequestEntry", parameters) { (messageData, id) =>
            doChangeMessageVisibility(queue, messageData)

            <ChangeMessageVisibilityBatchResultEntry>
              <Id>{id}</Id>
            </ChangeMessageVisibilityBatchResultEntry>
          }

          respondWith {
            <ChangeMessageVisibilityBatchResponse>
              <ChangeMessageVisibilityBatchResult>
                {results}
              </ChangeMessageVisibilityBatchResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </ChangeMessageVisibilityBatchResponse>
          }
        }
      }
    }
  }
}