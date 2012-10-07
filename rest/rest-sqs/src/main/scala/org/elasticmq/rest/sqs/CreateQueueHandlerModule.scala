package org.elasticmq.rest.sqs


import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import ParametersUtil._
import org.joda.time.Duration
import org.elasticmq.{QueueBuilder, MillisVisibilityTimeout}

trait CreateQueueHandlerModule { this: ClientModule with QueueURLModule with RequestHandlerLogicModule
  with AttributesModule =>

  val DefaultVisibilityTimeout = 30L;
  val DefaultDelay = 0L;

  val CreateQueueAction = createAction("CreateQueue")

  val createQueueLogic = logicWithQueueName((queueName, request, parameters) => {
    val queueOption = client.lookupQueue(queueName)

    val attributes = attributeNameAndValuesReader.read(parameters)

    val secondsVisibilityTimeout =
      (attributes.parseOptionalLong(VisibilityTimeoutParameter)
              .getOrElse(DefaultVisibilityTimeout));

    val secondsDelay =
      (attributes.parseOptionalLong(DelayParameter)
        .getOrElse(DefaultDelay));

    val queue = queueOption.getOrElse(client.createQueue(
      QueueBuilder(queueName)
        .withDefaultVisibilityTimeout(MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout))
        .withDelay(Duration.standardSeconds(secondsDelay))))

    if (queue.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout) {
      // Special case: the queue existed, but has a different visibility timeout
      throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
    }

    <CreateQueueResponse>
      <CreateQueueResult>
        <QueueUrl>{queueURL(queue)}</QueueUrl>
      </CreateQueueResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </CreateQueueResponse>
  })

  val createQueueGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameters List(QueueNameParameter)
            requiringParameterValues Map(CreateQueueAction)
            running createQueueLogic)

  val createQueuePostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody()
            requiringParameters List(QueueNameParameter)
            requiringParameterValues Map(CreateQueueAction)
            running createQueueLogic)
}