package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait DeleteQueueHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val deleteQueueLogic = logicWithQueue((queue, request, parameters) => {
    client.queueClient.deleteQueue(queue)

    <DeleteQueueResponse>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </DeleteQueueResponse>
  })

  val DeleteQueueAction = createAction("DeleteQueue")

  val deleteQueueGetHandler = (createHandler
          forMethod GET
          forPath (QueuePath)
          requiringParameterValues Map(DeleteQueueAction)
          running deleteQueueLogic)

  val deleteQueuePostHandler = (createHandler
          forMethod POST
          forPath (QueuePath)
          includingParametersFromBody()
          requiringParameterValues Map(DeleteQueueAction)
          running deleteQueueLogic)
}