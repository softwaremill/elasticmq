package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait ChangeMessageVisibilityBatchDirectives { this: ElasticMQDirectives with ChangeMessageVisibilityDirectives with BatchRequestsModule =>
  val changeMessageVisibilityBatch = {
    action("ChangeMessageVisibilityBatch") {
      queueActorFromPath { queueActor =>
        anyParamsMap { parameters =>
          val resultsFuture = batchRequest("ChangeMessageVisibilityBatchRequestEntry", parameters) { (messageData, id) =>
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
}