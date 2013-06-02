package org.elasticmq.rest.sqs

import Constants._
import ParametersUtil._
import org.joda.time.{DateTime, Duration}
import org.elasticmq.{QueueData, MillisVisibilityTimeout}
import org.elasticmq.msg.{GetQueueData, CreateQueue, LookupQueue}
import org.elasticmq.actor.reply._
import akka.dataflow._
import scala.concurrent.Future
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait CreateQueueDirectives { this: ElasticMQDirectives with QueueURLModule with AttributesModule with SQSLimitsModule =>
  val DefaultVisibilityTimeout = 30L
  val DefaultDelay = 0L
  val DefaultReceiveMessageWaitTimeSecondsAttribute = 0L

  val createQueue = {
    action("CreateQueue") {
      rootPath {
        queueNameFromParams { queueName =>
          anyParamsMap { parameters =>
            val attributes = attributeNameAndValuesReader.read(parameters)

            val secondsVisibilityTimeout = (attributes.parseOptionalLong(VisibilityTimeoutParameter)
                .getOrElse(DefaultVisibilityTimeout))

            val secondsDelay = (attributes.parseOptionalLong(DelaySecondsAttribute)
                .getOrElse(DefaultDelay))

            val secondsReceiveMessageWaitTimeOpt = attributes.parseOptionalLong(ReceiveMessageWaitTimeSecondsAttribute)
            val secondsReceiveMessageWaitTime = secondsReceiveMessageWaitTimeOpt
              .getOrElse(DefaultReceiveMessageWaitTimeSecondsAttribute)

            val newQueueData = QueueData(queueName, MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout),
              Duration.standardSeconds(secondsDelay), Duration.standardSeconds(secondsReceiveMessageWaitTime),
              new DateTime(), new DateTime())

            flow {
              if (!queueName.matches("[\\p{Alnum}_-]*")) {
                throw SQSException.invalidParameterValue
              }

              if (secondsReceiveMessageWaitTime < 0) {
                throw SQSException.invalidParameterValue
              }

              secondsReceiveMessageWaitTimeOpt.foreach { specifiedSecondsReceiveMessageWaitTime =>
                ifStrictLimits(specifiedSecondsReceiveMessageWaitTime > 20 || specifiedSecondsReceiveMessageWaitTime < 1) {
                  InvalidParameterValueErrorName
                }
              }

              val queueData = lookupOrCreateQueue(newQueueData).apply()

              if ((queueData.delay.getStandardSeconds != secondsDelay) ||
                (queueData.receiveMessageWait.getStandardSeconds != secondsReceiveMessageWaitTime) ||
                (queueData.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout)) {
                // Special case: the queue existed, but has different attributes
                throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
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
            case Left(e) => throw new SQSException("Concurrent access: " + e.message)
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
