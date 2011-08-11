package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import org.elasticmq.rest.sqs.ActionUtil._

trait DeleteMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  import DeleteMessageHandlerModule._

  val deleteMessageLogic = logicWithQueue((queue, request, parameters) => {
    val id = parameters(RECEIPT_HANDLE_PARAMETER)
    val messageOption = client.messageClient.lookupMessage(id)
    // No failure even if the message doesn't exist
    messageOption.foreach(client.messageClient.deleteMessage(_))

    <DeleteMessageResponse>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </DeleteMessageResponse>
  })

  val deleteMessageGetHandler = (createHandler
            forMethod GET
            forPath (QUEUE_PATH)
            requiringParameters List(RECEIPT_HANDLE_PARAMETER)
            requiringParameterValues Map(DELETE_MESSAGE_ACTION)
            running deleteMessageLogic)
}

object DeleteMessageHandlerModule {
  val DELETE_MESSAGE_ACTION = createAction("DeleteMessage")
}