package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.actor.reply._
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait DeleteMessageDirectives { this: ElasticMQDirectives =>
  val deleteMessage = {
    action("DeleteMessage") {
      queueActorFromRequest { queueActor =>
        anyParam(ReceiptHandleParameter) { receipt =>
          val result = queueActor ? DeleteMessage(DeliveryReceipt(receipt))

          result.map { _ =>
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
}