package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait DeleteQueueHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  import DeleteQueueHandlerModule._

  val deleteQueueLogic = logicWithQueue((queue, request, parameters) => {
    client.queueClient.deleteQueue(queue)

    <DeleteQueueResponse>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </DeleteQueueResponse>
  })

  val deleteQueueGetHandler = (createHandler
          forMethod GET
          forPath (QUEUE_PATH)
          requiringParameterValues Map(DELETE_QUEUE_ACTION)
          running deleteQueueLogic)
}

object DeleteQueueHandlerModule {
  val DELETE_QUEUE_ACTION = createAction("DeleteQueue")
}