package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.CreateQueueDirectives._
import org.elasticmq.rest.sqs.ParametersUtil._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RedrivePolicy.BackwardCompatibleRedrivePolicy
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData, Limits}
import org.joda.time.{DateTime, Duration}
import spray.json.JsonParser.ParsingException
import spray.json._

import scala.async.Async._
import scala.concurrent.Future

trait CreateQueueDirectives {
  this: ElasticMQDirectives with QueueURLModule with AttributesModule with TagsModule with SQSLimitsModule =>

  def createQueue(p: AnyParams) = {
    p.action("CreateQueue") {
      rootPath {
        queueNameFromParams(p) { queueName =>
          val attributes = attributeNameAndValuesReader.read(p)

          val redrivePolicy =
            try {
              import org.elasticmq.rest.sqs.model.RedrivePolicyJson._
              attributes
                .get(RedrivePolicyParameter)
                .map(_.parseJson.convertTo[BackwardCompatibleRedrivePolicy])
            } catch {
              case e: DeserializationException =>
                logger.warn("Cannot deserialize the redrive policy attribute", e)
                throw new SQSException("MalformedQueryString")
              case e: ParsingException =>
                logger.warn("Cannot parse the redrive policy attribute", e)
                throw new SQSException("MalformedQueryString")
            }

          async {
            redrivePolicy match {
              case Some(rd) =>
                if (await(queueManagerActor ? LookupQueue(rd.queueName)).isEmpty) {
                  throw SQSException.nonExistentQueue
                }

                if (rd.maxReceiveCount < 1 || rd.maxReceiveCount > 1000) {
                  throw SQSException.invalidParameterValue
                }
              case None =>
            }

            val secondsVisibilityTimeoutOpt =
              attributes.parseOptionalLong(VisibilityTimeoutParameter)
            val secondsVisibilityTimeout =
              secondsVisibilityTimeoutOpt.getOrElse(DefaultVisibilityTimeout)

            val secondsDelayOpt =
              attributes.parseOptionalLong(DelaySecondsAttribute)
            val secondsDelay = secondsDelayOpt.getOrElse(DefaultDelay)

            val secondsReceiveMessageWaitTimeOpt = attributes.parseOptionalLong(ReceiveMessageWaitTimeSecondsAttribute)
            val secondsReceiveMessageWaitTime = secondsReceiveMessageWaitTimeOpt
              .getOrElse(DefaultReceiveMessageWait)

            val now = new DateTime()
            val isFifo = attributes.get("FifoQueue").contains("true")
            val hasContentBasedDeduplication = attributes.get("ContentBasedDeduplication").contains("true")

            val newQueueData = QueueData(
              queueName,
              MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout),
              Duration.standardSeconds(secondsDelay),
              Duration.standardSeconds(secondsReceiveMessageWaitTime),
              now,
              now,
              redrivePolicy.map(rd => DeadLettersQueueData(rd.queueName, rd.maxReceiveCount)),
              isFifo,
              hasContentBasedDeduplication,
              tags = tagNameAndValuesReader.read(p)
            )

            secondsReceiveMessageWaitTimeOpt.foreach(messageWaitTime =>
              Limits
                .verifyMessageWaitTime(messageWaitTime, sqsLimits)
                .fold(error => throw new SQSException(error), identity)
            )

            val queueData = await(lookupOrCreateQueue(newQueueData))

            // if the request set the attributes compare them against the queue
            if (
              (secondsDelayOpt.isDefined && queueData.delay.getStandardSeconds != secondsDelay) ||
              (secondsReceiveMessageWaitTimeOpt.isDefined
                && queueData.receiveMessageWait.getStandardSeconds != secondsReceiveMessageWaitTime) ||
              (secondsVisibilityTimeoutOpt.isDefined
                && queueData.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout)
            ) {
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
      val queueActorOption =
        await(queueManagerActor ? LookupQueue(newQueueData.name))
      queueActorOption match {
        case None =>
          val createResult =
            await(queueManagerActor ? CreateQueue(newQueueData))
          createResult match {
            case Left(e) =>
              throw new SQSException("Concurrent access: " + e.message)
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
  val DefaultReceiveMessageWait = 0L
}
