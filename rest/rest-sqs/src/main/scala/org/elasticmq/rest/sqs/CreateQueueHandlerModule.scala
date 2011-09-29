package org.elasticmq.rest.sqs

import org.elasticmq.Queue
import org.elasticmq.VisibilityTimeout

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import ParametersParserUtil._

trait CreateQueueHandlerModule { this: ClientModule with QueueURLModule with RequestHandlerLogicModule =>
  import CreateQueueHandlerModule._

  val createQueueLogic = logicWithQueueName((queueName, request, parameters) => {
    val queueOption = client.queueClient.lookupQueue(queueName)

    val secondsVisibilityTimeout =
      (parameters.parseOptionalLong("DefaultVisibilityTimeout")
              .getOrElse(DEFAULT_VISIBILITY_TIMEOUT));

    val queue = queueOption.getOrElse(client.queueClient.createQueue(
      Queue(queueName, VisibilityTimeout.fromSeconds(secondsVisibilityTimeout))))

    if (queue.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout) {
      // Special case: the queue existed, but has a different visibility timeout
      throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
    }

    <CreateQueueResponse>
      <CreateQueueResult>
        <QueueUrl>{queueURL(queue)}</QueueUrl>
      </CreateQueueResult>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </CreateQueueResponse>
  })

  val createQueueGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameters List(QUEUE_NAME_PARAMETER)
            requiringParameterValues Map(CREATE_QUEUE_ACTION)
            running createQueueLogic)

  val createQueuePostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody()
            requiringParameters List(QUEUE_NAME_PARAMETER)
            requiringParameterValues Map(CREATE_QUEUE_ACTION)
            running createQueueLogic)
}

object CreateQueueHandlerModule {
  val DEFAULT_VISIBILITY_TIMEOUT = 30L;
  val CREATE_QUEUE_ACTION = createAction("CreateQueue")
}