package org.elasticmq.rest.sqs

import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._
import org.elasticmq.msg.UpdateVisibilityTimeout
import org.elasticmq.rest.sqs.Action.ChangeMessageVisibility
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}

trait ChangeMessageVisibilityDirectives { this: ElasticMQDirectives =>
  def changeMessageVisibility(p: AnyParams, protocol: AWSProtocol) = {
    p.action(ChangeMessageVisibility) {
      queueActorFromRequest(p) { queueActor =>
        (p.requiredParam(ReceiptHandleParameter) and p.requiredParam(VisibilityTimeoutParameter)) {
          (receipt, visibilityTimeout) =>
            val result = queueActor ? UpdateVisibilityTimeout(
              DeliveryReceipt(receipt),
              MillisVisibilityTimeout.fromSeconds(visibilityTimeout.toLong)
            )
            result.map {
              case Left(error) => throw new SQSException(error.code, errorMessage = Some(error.message))
              case Right(_) =>
                protocol match {
                  case AWSProtocol.AWSQueryProtocol =>
                    respondWith {
                      <ChangeMessageVisibilityResponse>
                        <ResponseMetadata>
                          <RequestId>
                            {EmptyRequestId}
                          </RequestId>
                        </ResponseMetadata>
                      </ChangeMessageVisibilityResponse>
                    }
                  case _ => complete(status = 200, HttpEntity.Empty)
                }
            }
        }
      }
    }
  }
}
