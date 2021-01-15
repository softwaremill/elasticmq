package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.actor.reply._
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.rest.sqs.Action.{DeleteMessage => DeleteMessageAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait DeleteMessageDirectives { this: ElasticMQDirectives =>
  def deleteMessage(p: AnyParams) = {
    p.action(DeleteMessageAction) {
      queueActorFromRequest(p) { queueActor =>
        p.requiredParam(ReceiptHandleParameter) { receipt =>
          val result = queueActor ? DeleteMessage(DeliveryReceipt(receipt))

          result.map {
            case Left(error) => throw new SQSException(error.code, errorMessage = Some(error.message))
            case Right(_) =>
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
