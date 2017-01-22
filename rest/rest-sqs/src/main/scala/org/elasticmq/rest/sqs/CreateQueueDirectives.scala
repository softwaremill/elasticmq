package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.CreateQueueDirectives._
import org.elasticmq.rest.sqs.ParametersUtil._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.{MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}
import spray.json._

import scala.async.Async._
import scala.concurrent.Future

trait CreateQueueDirectives { this: ElasticMQDirectives with QueueURLModule with AttributesModule with SQSLimitsModule =>

  def createQueue(p: AnyParams) = {
    p.action("CreateQueue") {
      rootPath {
        queueNameFromParams(p) { queueName =>
          val attributes = attributeNameAndValuesReader.read(p)

          import RedrivePolicyJson._
          val redrivePolicyJSON = attributes.get(RedrivePolicyParameter)
          val redrivePolicy = redrivePolicyJSON.map(_.parseJson.convertTo[RedrivePolicy])

          val secondsVisibilityTimeoutOpt = attributes.parseOptionalLong(VisibilityTimeoutParameter)
          val secondsVisibilityTimeout = secondsVisibilityTimeoutOpt.getOrElse(DefaultVisibilityTimeout)

          val secondsDelayOpt = attributes.parseOptionalLong(DelaySecondsAttribute)
          val secondsDelay = secondsDelayOpt.getOrElse(DefaultDelay)

          val secondsReceiveMessageWaitTimeOpt = attributes.parseOptionalLong(ReceiveMessageWaitTimeSecondsAttribute)
          val secondsReceiveMessageWaitTime = secondsReceiveMessageWaitTimeOpt
            .getOrElse(DefaultReceiveMessageWait)

          val now = new DateTime()
          val newQueueData = QueueData(queueName, MillisVisibilityTimeout.fromSeconds(secondsVisibilityTimeout),
            Duration.standardSeconds(secondsDelay), Duration.standardSeconds(secondsReceiveMessageWaitTime),
            now, now, redrivePolicy.map(redrivePolicyToQueueData(_, now)))

          async {
            if (!queueName.matches("[\\p{Alnum}_-]*")) {
              throw SQSException.invalidParameterValue
            } else if (sqsLimits == SQSLimits.Strict && queueName.length() > 80) {
              throw SQSException.invalidParameterValue
            }

            verifyMessageWaitTime(secondsReceiveMessageWaitTimeOpt)

            val queueData = await(lookupOrCreateQueue(newQueueData))

            // if the request set the attributes compare them against the queue
            if ((!secondsDelayOpt.isEmpty && queueData.delay.getStandardSeconds != secondsDelay) ||
              (!secondsReceiveMessageWaitTimeOpt.isEmpty
                && queueData.receiveMessageWait.getStandardSeconds != secondsReceiveMessageWaitTime) ||
              (!secondsVisibilityTimeoutOpt.isEmpty
                && queueData.defaultVisibilityTimeout.seconds != secondsVisibilityTimeout)) {
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

  private def redrivePolicyToQueueData(rd: RedrivePolicy, now: DateTime): QueueData = {
    import rd._
    QueueData(
      name = queueName,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        defaultVisibilityTimeout.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)),
      delay = Duration.standardSeconds(delay.getOrElse(CreateQueueDirectives.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        receiveMessageWait.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWait)),
      created = now,
      lastModified = now,
      maxReceiveCount = Some(maxReceiveCount),
      isDeadLettersQueue = true,
      deadLettersQueue = deadLettersQueue.map(redrivePolicyToQueueData(_, now))
    ).adjustLimits()
  }
}

object CreateQueueDirectives {
  val DefaultVisibilityTimeout = 30L
  val DefaultDelay = 0L
  val DefaultReceiveMessageWait = 0L
}

case class RedrivePolicy(
  queueName: String,
  maxReceiveCount: Int,
  defaultVisibilityTimeout: Option[Long] = None,
  delay: Option[Long] = None,
  receiveMessageWait: Option[Long] = None,
  deadLettersQueue: Option[RedrivePolicy] = None
)

object RedrivePolicyJson extends DefaultJsonProtocol {
  implicit val format: JsonFormat[RedrivePolicy] =
    lazyFormat(jsonFormat(
      RedrivePolicy,
      "queueName",
      "defaultVisibilityTimeout",
      "delay",
      "receiveMessageWait",
      "maxReceiveCount",
      "deadLettersQueue"))
}