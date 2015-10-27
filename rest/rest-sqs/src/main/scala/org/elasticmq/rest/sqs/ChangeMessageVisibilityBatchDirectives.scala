package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait ChangeMessageVisibilityBatchDirectives { this: ElasticMQDirectives with ChangeMessageVisibilityDirectives with BatchRequestsModule =>
  def changeMessageVisibilityBatch(p: AnyParams) = {
    p.action("ChangeMessageVisibilityBatch") {
      queueActorFromRequest(p) { queueActor =>
        val resultsFuture = batchRequest("ChangeMessageVisibilityBatchRequestEntry", p) { (messageData, id) =>
          doChangeMessageVisibility(queueActor, messageData).map { _ =>
            <ChangeMessageVisibilityBatchResultEntry>
              <Id>{id}</Id>
            </ChangeMessageVisibilityBatchResultEntry>
          }
        }

        resultsFuture.map { results =>
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