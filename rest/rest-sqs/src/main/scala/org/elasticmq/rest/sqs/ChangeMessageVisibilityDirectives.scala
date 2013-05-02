package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.{DeliveryReceipt, Queue, MillisVisibilityTimeout}

trait ChangeMessageVisibilityDirectives { this: ElasticMQDirectives =>
  val changeMessageVisibility = {
    action("ChangeMessageVisibility") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          doChangeMessageVisibility(queue, parameters)

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

  def doChangeMessageVisibility(queue: Queue, parameters: Map[String, String]) {
    val visibilityTimeout = MillisVisibilityTimeout.fromSeconds(parameters(VisibilityTimeoutParameter).toLong)
    val message = queue.lookupMessage(DeliveryReceipt(parameters(ReceiptHandleParameter)))
      .getOrElse(throw SQSException.invalidParameterValue)
    message.updateVisibilityTimeout(visibilityTimeout)
  }
}