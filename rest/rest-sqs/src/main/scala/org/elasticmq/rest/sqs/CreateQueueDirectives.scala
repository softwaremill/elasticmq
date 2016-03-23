package org.elasticmq.rest.sqs

import Constants._
import ParametersUtil._
import org.joda.time.{DateTime, Duration}
import org.elasticmq.{QueueData, MillisVisibilityTimeout}
import org.elasticmq.msg.{GetQueueData, CreateQueue, LookupQueue}
import org.elasticmq.actor.reply._
import scala.async.Async._
import scala.concurrent.Future
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import CreateQueueDirectives._

trait CreateQueueDirectives { this: ElasticMQDirectives with QueueURLModule with AttributesModule with SQSLimitsModule =>

  def createQueue(p: AnyParams) = {
    p.action("CreateQueue") {
      rootPath {
        queueNameFromParams(p) { queueName =>
          val attributes = attributeNameAndValuesReader.read(p)

          val secondsVisibilityTimeout = attributes.parseOptionalLong(VisibilityTimeoutParameter)
            .getOrElse(DefaultVisibilityTimeout)

          val secondsDelay = attributes.parseOptionalLong(DelaySecondsAttribute)
            .getOrElse(DefaultDelay)

          val secondsReceiveMessageWaitTimeOpt = attributes.parseOptionalLong(ReceiveMessageWaitTimeSecondsAttribute)
          val secondsReceiveMessageWaitTime = secondsReceiveMessageWaitTimeOpt
            .getOrElse(DefaultReceiveMessageWaitTimeSecondsAttribute)

          val newQueueData = QueueData(queueName, MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout),
            Duration.standardSeconds(secondsDelay), Duration.standardSeconds(secondsReceiveMessageWaitTime),
            new DateTime(), new DateTime())

          async {
            if (!queueName.matches("[\\p{Alnum}_-]*")) {
              throw SQSException.invalidParameterValue
            } else if (sqsLimits == SQSLimits.Strict && queueName.length() > 80) {
              throw SQSException.invalidParameterValue
            }

            verifyMessageWaitTime(secondsReceiveMessageWaitTimeOpt)

            val queueData = await(lookupOrCreateQueue(newQueueData))

            if ((queueData.delay.getStandardSeconds != secondsDelay) ||
              (queueData.receiveMessageWait.getStandardSeconds != secondsReceiveMessageWaitTime) ||
              (queueData.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout)) {
              // Special case: the queue existed, but has different attributes
              throw new SQSException("AWS.SimpleQueueService.QueueNameExists")
            }

            queueURL(queueData) { url =>
              respondWith {
                <CreateQueueResponse>
                  <CreateQueueResult>
                    <QueueUrl>{url}</QueueUrl>
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
    async {
      val queueActorOption = await(queueManagerActor ? LookupQueue(newQueueData.name))
      queueActorOption match {
        case None =>
          val createResult = await(queueManagerActor ? CreateQueue(newQueueData))
          createResult match {
            case Left(e) => throw new SQSException("Concurrent access: " + e.message)
            case Right(_) => newQueueData
          }
        case Some(queueActor) =>
          await(queueActor ? GetQueueData())
      }
    }
  }
}

object CreateQueueDirectives {
  val DefaultVisibilityTimeout = 30L
  val DefaultDelay = 0L
  val DefaultReceiveMessageWaitTimeSecondsAttribute = 0L
}