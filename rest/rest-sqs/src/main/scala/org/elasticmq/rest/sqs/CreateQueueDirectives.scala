package org.elasticmq.rest.sqs

import Constants._
import ParametersUtil._
import org.joda.time.Duration
import org.elasticmq.{QueueBuilder, MillisVisibilityTimeout}

trait CreateQueueDirectives { this: ElasticMQDirectives with QueueURLModule with AttributesModule with SQSLimitsModule =>
  val DefaultVisibilityTimeout = 30L
  val DefaultDelay = 0L

  val createQueue = {
    action("CreateQueue") {
      rootPath {
        withQueueName { queueName =>
          anyParamsMap { parameters =>
            val queueOption = client.lookupQueue(queueName)

            val attributes = attributeNameAndValuesReader.read(parameters)

            val secondsVisibilityTimeout =
              (attributes.parseOptionalLong(VisibilityTimeoutParameter)
                .getOrElse(DefaultVisibilityTimeout))

            val secondsDelay =
              (attributes.parseOptionalLong("DelaySeconds")
                .getOrElse(DefaultDelay))

            val queue = queueOption.getOrElse(client.createQueue(
              QueueBuilder(queueName)
                .withDefaultVisibilityTimeout(MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout))
                .withDelay(Duration.standardSeconds(secondsDelay))))

            if (queue.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout) {
              // Special case: the queue existed, but has a different visibility timeout
              throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
            }

            if (!queueName.matches("[\\p{Alnum}_-]*")) {
              throw new SQSException("InvalidParameterValue")
            }

            respondWith {
              <CreateQueueResponse>
                <CreateQueueResult>
                  <QueueUrl>{queueURL(queue)}</QueueUrl>
                </CreateQueueResult>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </CreateQueueResponse>
            }
          }
        }
      }
    }
  }
}
