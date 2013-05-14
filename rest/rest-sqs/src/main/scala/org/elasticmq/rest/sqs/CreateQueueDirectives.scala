package org.elasticmq.rest.sqs

import Constants._
import ParametersUtil._
import org.joda.time.{DateTime, Duration}
import org.elasticmq.MillisVisibilityTimeout
import org.elasticmq.msg.{GetQueueData, CreateQueue, LookupQueue}
import org.elasticmq.actor.reply._
import org.elasticmq.data.QueueData
import akka.dataflow._
import scala.concurrent.Future

trait CreateQueueDirectives { this: ElasticMQDirectives with QueueURLModule with AttributesModule with SQSLimitsModule =>
  val DefaultVisibilityTimeout = 30L
  val DefaultDelay = 0L

  val createQueue = {
    action("CreateQueue") {
      rootPath {
        queueNameFromParams { queueName =>
          anyParamsMap { parameters =>
            val attributes = attributeNameAndValuesReader.read(parameters)

            val secondsVisibilityTimeout = (attributes.parseOptionalLong(VisibilityTimeoutParameter)
                .getOrElse(DefaultVisibilityTimeout))

            val secondsDelay = (attributes.parseOptionalLong("DelaySeconds")
                .getOrElse(DefaultDelay))

            val newQueueData = QueueData(queueName, MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout),
              Duration.standardSeconds(secondsDelay), new DateTime(), new DateTime())

            flow {
              val queueData = lookupOrCreateQueue(newQueueData).apply()

              if (queueData.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout) {
                // Special case: the queue existed, but has a different visibility timeout
                throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
              }

              if (!queueName.matches("[\\p{Alnum}_-]*")) {
                throw SQSException.invalidParameterValue
              }

              respondWith {
                <CreateQueueResponse>
                  <CreateQueueResult>
                    <QueueUrl>{queueURL(queueData)}</QueueUrl>
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

  private def lookupOrCreateQueue[T](newQueueData: QueueData): Future[QueueData] = {
    flow {
      val queueActorOption = (queueManagerActor ? LookupQueue(newQueueData.name)).apply()
      queueActorOption match {
        case None => {
          val createResult = (queueManagerActor ? CreateQueue(newQueueData)).apply()
          createResult match {
            case Left(_) => throw new SQSException("Concurrent access")
            case Right(_) => newQueueData
          }
        }
        case Some(queueActor) => {
          (queueActor ? GetQueueData()).apply()
        }
      }
    }
  }
}
