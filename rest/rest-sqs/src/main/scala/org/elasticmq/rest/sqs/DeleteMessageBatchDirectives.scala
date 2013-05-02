package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt

trait DeleteMessageBatchDirectives { this: ElasticMQDirectives with BatchRequestsModule =>
  val deleteMessageBatch = {
    action("DeleteMessageBatch") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          val results = batchRequest("DeleteMessageBatchRequestEntry", parameters) { (messageData, id) =>
            val receiptHandle = messageData(ReceiptHandleParameter)
            val messageOption = queue.lookupMessage(DeliveryReceipt(receiptHandle))
            // No failure even if the message doesn't exist
            messageOption.foreach(_.delete())

            <DeleteMessageBatchResultEntry>
              <Id>{id}</Id>
            </DeleteMessageBatchResultEntry>
          }

          respondWith {
            <DeleteMessageBatchResponse>
              <DeleteMessageBatchResult>
                {results}
              </DeleteMessageBatchResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </DeleteMessageBatchResponse>
          }
        }
      }
    }
  }
}