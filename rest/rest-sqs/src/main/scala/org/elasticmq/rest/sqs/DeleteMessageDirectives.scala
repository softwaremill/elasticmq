package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.actor.reply._
import org.elasticmq.msg.DeleteMessage

trait DeleteMessageDirectives { this: ElasticMQDirectives =>
  val deleteMessage = {
    action("DeleteMessage") {
      queueActorFromPath { queueActor =>
        anyParam(ReceiptHandleParameter) { receipt =>
          val msgId = DeliveryReceipt(receipt).extractId
          val result = queueActor ? DeleteMessage(msgId)

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