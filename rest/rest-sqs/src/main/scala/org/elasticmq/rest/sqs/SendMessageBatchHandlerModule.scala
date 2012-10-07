package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import MD5Util._
import ParametersUtil._
import org.elasticmq.{MessageBuilder, AfterMillisNextDelivery, Queue}

trait SendMessageBatchHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val sendMessageBatchLogic = logicWithQueue((queue, request, parameters) => {
    <SendMessageBatchResponse>
      <SendMessageBatchResult>
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
