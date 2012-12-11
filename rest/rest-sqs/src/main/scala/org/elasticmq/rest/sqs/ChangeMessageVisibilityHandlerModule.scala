package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import org.elasticmq.{DeliveryReceipt, Queue, MessageId, MillisVisibilityTimeout}

trait ChangeMessageVisibilityHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val changeMessageVisibilityLogic = logicWithQueue((queue, request, parameters) => {
    changeMessageVisibility(queue, parameters)

    <ChangeMessageVisibilityResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </ChangeMessageVisibilityResponse>
  })

  def changeMessageVisibility(queue: Queue, parameters: Map[String, String]) {
    val visibilityTimeout = MillisVisibilityTimeout.fromSeconds(parameters(VisibilityTimeoutParameter).toLong)
    val message = queue.lookupMessage(DeliveryReceipt(parameters(ReceiptHandleParameter)))
      .getOrElse(throw SQSException.invalidParameterValue)
    message.updateVisibilityTimeout(visibilityTimeout)
  }

  private val ChangeMessageVisibilityAction = createAction("ChangeMessageVisibility")

  val changeMessageVisibilityGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameters List(ReceiptHandleParameter, VisibilityTimeoutParameter)
            requiringParameterValues Map(ChangeMessageVisibilityAction)
            running changeMessageVisibilityLogic)

  val changeMessageVisibilityPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameters List(ReceiptHandleParameter, VisibilityTimeoutParameter)
            requiringParameterValues Map(ChangeMessageVisibilityAction)
            running changeMessageVisibilityLogic)
}