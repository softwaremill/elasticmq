package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt

trait DeleteMessageDirectives { this: ElasticMQDirectives =>
  val deleteMessage = {
    action("DeleteMessage") {
      queuePath { queue =>
        anyParam(ReceiptHandleParameter) { receipt =>
          val messageOption = queue.lookupMessage(DeliveryReceipt(receipt))
          // No failure even if the message doesn't exist
          messageOption.foreach(_.delete())

          respondWith {
            <DeleteMessageResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </DeleteMessageResponse>
          }
        }
      }
    }
  }
}