package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait GetQueueUrlHandlerModule { this: ClientModule with QueueURLModule with RequestHandlerLogicModule =>
  val GetQueueUrlAction = createAction("GetQueueUrl")

  val getQueueUrlLogic = logicWithQueueName((queueName, request, parameters) => {
    val queueOption = client.queueClient.lookupQueue(queueName)

    queueOption match {
      case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
      case Some(queue) => {
        <GetQueueUrlResponse>
          <GetQueueUrlResult>
            <QueueUrl>{queueURL(queue)}</QueueUrl>
          </GetQueueUrlResult>
          <ResponseMetadata>
            <RequestId>{EmptyRequestId}</RequestId>
          </ResponseMetadata>
        </GetQueueUrlResponse>
      }
    }
  })

  val getQueueUrlGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameterValues Map(GetQueueUrlAction)
            running getQueueUrlLogic)

  val getQueueUrlPostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody()
            requiringParameterValues Map(GetQueueUrlAction)
            running getQueueUrlLogic)
}