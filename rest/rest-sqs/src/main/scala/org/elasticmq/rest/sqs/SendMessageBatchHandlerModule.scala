package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait SendMessageBatchHandlerModule { this: ClientModule with RequestHandlerLogicModule with SendMessageHandlerModule =>
  val sendMessageBatchLogic = logicWithQueue((queue, request, parameters) => {
    val messagesData = ParametersUtil.subParametersMaps("SendMessageBatchRequestEntry", parameters)

    val results = messagesData.map(messageData => {
      val id = messageData(IdSubParameter)
      val (message, digest) = sendMessage(queue, messageData)

      <SendMessageBatchResultEntry>
        <Id>{id}</Id>
        <MD5OfMessageBody>{digest}</MD5OfMessageBody>
        <MessageId>{message.id.id}</MessageId>
      </SendMessageBatchResultEntry>
    })

    <SendMessageBatchResponse>
      <SendMessageBatchResult>
        {results}
      </SendMessageBatchResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SendMessageBatchResponse>
  })

  val SendMessageBatchAction = createAction("SendMessageBatch")

  val sendMessageBatchGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(SendMessageBatchAction)
            running sendMessageBatchLogic)

  val sendMessageBatchPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameterValues Map(SendMessageBatchAction)
            running sendMessageBatchLogic)
}
