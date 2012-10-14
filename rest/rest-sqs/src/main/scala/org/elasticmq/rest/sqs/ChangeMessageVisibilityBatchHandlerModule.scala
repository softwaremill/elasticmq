package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait ChangeMessageVisibilityBatchHandlerModule { this: ClientModule with RequestHandlerLogicModule
  with ChangeMessageVisibilityHandlerModule with BatchRequestsModule =>

  val changeMessageVisibilityBatchLogic = logicWithQueue((queue, request, parameters) => {
    val results = batchRequest("ChangeMessageVisibilityBatchRequestEntry", parameters) { (messageData, id) =>
      changeMessageVisibility(queue, messageData)

      <ChangeMessageVisibilityBatchResultEntry>
        <Id>{id}</Id>
      </ChangeMessageVisibilityBatchResultEntry>
    }

    <ChangeMessageVisibilityBatchResponse>
      <ChangeMessageVisibilityBatchResult>
        {results}
      </ChangeMessageVisibilityBatchResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </ChangeMessageVisibilityBatchResponse>
  })

  private val ChangeMessageVisibilityBatchAction = createAction("ChangeMessageVisibilityBatch")

  val changeMessageVisibilityBatchGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(ChangeMessageVisibilityBatchAction)
            running changeMessageVisibilityBatchLogic)

  val changeMessageVisibilityBatchPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameterValues Map(ChangeMessageVisibilityBatchAction)
            running changeMessageVisibilityBatchLogic)
}