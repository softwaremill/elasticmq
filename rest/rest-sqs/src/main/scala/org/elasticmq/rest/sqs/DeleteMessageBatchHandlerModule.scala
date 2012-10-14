package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import org.elasticmq.rest.sqs.ActionUtil._
import org.elasticmq.MessageId

trait DeleteMessageBatchHandlerModule { this: ClientModule with RequestHandlerLogicModule with BatchRequestsModule  =>
  val deleteMessageBatchLogic = logicWithQueue((queue, request, parameters) => {
    val messagesData = batchParametersMap("DeleteMessageBatchRequestEntry", parameters)

    val results = messagesData.map(messageData => {
      val id = messageData(IdSubParameter)

      val receiptHandle = messageData(ReceiptHandleParameter)
      val messageOption = queue.lookupMessage(MessageId(receiptHandle))
      // No failure even if the message doesn't exist
      messageOption.foreach(_.delete())

      <DeleteMessageBatchResultEntry>
        <Id>{id}</Id>
      </DeleteMessageBatchResultEntry>
    })

    <DeleteMessageBatchResponse>
      <DeleteMessageBatchResult>
        {results}
      </DeleteMessageBatchResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </DeleteMessageBatchResponse>
  })

  private val DeleteMessageBatchAction = createAction("DeleteMessageBatch")

  val deleteMessageBatchGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(DeleteMessageBatchAction)
            running deleteMessageBatchLogic)

  val deleteMessageBatchPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody()
            requiringParameterValues Map(DeleteMessageBatchAction)
            running deleteMessageBatchLogic)
}