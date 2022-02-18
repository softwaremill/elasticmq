package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}
import org.elasticmq.msg.UpdateVisibilityTimeout
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.Action.ChangeMessageVisibilityBatch
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait ChangeMessageVisibilityBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule =>
  def changeMessageVisibilityBatch(p: AnyParams) = {
    p.action(ChangeMessageVisibilityBatch) {
      queueActorFromRequest(p) { queueActor =>
        val resultsFuture =
          batchRequest("ChangeMessageVisibilityBatchRequestEntry", p) { (messageData, id, _) =>
            val receiptHandle = messageData(ReceiptHandleParameter)
            val visibilityTimeout = MillisVisibilityTimeout.fromSeconds(messageData(VisibilityTimeoutParameter).toLong)
            val result = queueActor ? UpdateVisibilityTimeout(DeliveryReceipt(receiptHandle), visibilityTimeout)

            result.map {
              case Left(error) =>
                <BatchResultErrorEntry>
                  <Id>{id}</Id>
                  <Code>{error.code}</Code>
                  <Message>{error.message}</Message>
                </BatchResultErrorEntry>
              case Right(_) =>
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
