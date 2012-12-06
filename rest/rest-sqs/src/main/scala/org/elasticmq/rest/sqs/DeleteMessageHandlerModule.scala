package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import org.elasticmq.rest.sqs.ActionUtil._
import org.elasticmq.DeliveryReceipt

trait DeleteMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val deleteMessageLogic = logicWithQueue((queue, request, parameters) => {
    val receipt = parameters(ReceiptHandleParameter)
    val messageOption = queue.lookupMessage(DeliveryReceipt(receipt))
    // No failure even if the message doesn't exist
    messageOption.foreach(_.delete())

    <DeleteMessageResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </DeleteMessageResponse>
  })

  val DeleteMessageAction = createAction("DeleteMessage")

  val deleteMessageGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameters List(ReceiptHandleParameter)
            requiringParameterValues Map(DeleteMessageAction)
            running deleteMessageLogic)

  val deleteMessagePostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameters List(ReceiptHandleParameter)
            requiringParameterValues Map(DeleteMessageAction)
            running deleteMessageLogic)
}