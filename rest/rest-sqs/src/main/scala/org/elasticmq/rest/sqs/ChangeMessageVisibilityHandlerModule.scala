package org.elasticmq.rest.sqs

import org.elasticmq.VisibilityTimeout
import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait ChangeMessageVisibilityHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val ChangeMessageVisibilityAction = createAction("ChangeMessageVisibility")
  val VisibilityTimeoutParameter = "VisibilityTimeout"

  val changeMessageVisibilityLogic = logicWithQueue((queue, request, parameters) => {
    val visibilityTimeout = VisibilityTimeout.fromSeconds(parameters(VisibilityTimeoutParameter).toLong)
    val message = client.messageClient.lookupMessage(parameters(ReceiptHandlerParameter))
      .getOrElse(throw SQSException.invalidParameterValue)
    client.messageClient.updateVisibilityTimeout(message, visibilityTimeout)

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