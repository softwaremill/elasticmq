package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}
import akka.actor.ActorRef
import org.elasticmq.actor.reply._
import org.elasticmq.msg.UpdateVisibilityTimeout

trait ChangeMessageVisibilityDirectives { this: ElasticMQDirectives =>
  val changeMessageVisibility = {
    action("ChangeMessageVisibility") {
      queueActorFromPath { queueActor =>
        anyParamsMap { parameters =>
          doChangeMessageVisibility(queueActor, parameters).map { _ =>
            respondWith {
              <ChangeMessageVisibilityResponse>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </ChangeMessageVisibilityResponse>
            }
          }
        }
      }
    }
  }

  def doChangeMessageVisibility(queueActor: ActorRef, parameters: Map[String, String]) = {
    val visibilityTimeout = MillisVisibilityTimeout.fromSeconds(parameters(VisibilityTimeoutParameter).toLong)
    val msgId = DeliveryReceipt(parameters(ReceiptHandleParameter)).extractId

    for {
      updateResult <- queueActor ? UpdateVisibilityTimeout(msgId, visibilityTimeout)
    } yield {
      updateResult match {
        case Left(_) => throw SQSException.invalidParameterValue
        case Right(_) => // ok
      }
    }
  }
}