package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import org.elasticmq.{MessageId, MillisVisibilityTimeout}

trait ChangeMessageVisibilityHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val ChangeMessageVisibilityAction = createAction("ChangeMessageVisibility")

  val changeMessageVisibilityLogic = logicWithQueue((queue, request, parameters) => {
    val visibilityTimeout = MillisVisibilityTimeout.fromSeconds(parameters(VisibilityTimeoutParameter).toLong)
    val message = queue.lookupMessage(MessageId(parameters(ReceiptHandlerParameter)))
      .getOrElse(throw SQSException.invalidParameterValue)
    message.updateVisibilityTimeout(visibilityTimeout)

    <ChangeMessageVisibilityResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </ChangeMessageVisibilityResponse>
  })

  val changeMessageVisibilityGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameters List(ReceiptHandlerParameter, VisibilityTimeoutParameter)
            requiringParameterValues Map(ChangeMessageVisibilityAction)
            running changeMessageVisibilityLogic)

  val changeMessageVisibilityPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameters List(ReceiptHandlerParameter, VisibilityTimeoutParameter)
            requiringParameterValues Map(ChangeMessageVisibilityAction)
            running changeMessageVisibilityLogic)
}