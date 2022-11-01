package org.elasticmq.rest.sqs

import org.elasticmq._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{LookupQueue, CreateQueue => CreateQueueMsg}
import org.elasticmq.rest.sqs.Action.CreateQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.ParametersUtil._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RedrivePolicy.BackwardCompatibleRedrivePolicy
import org.joda.time.Duration
import spray.json.JsonParser.ParsingException
import spray.json._

import scala.async.Async._
import scala.concurrent.Future

trait CreateQueueDirectives {
  this: ElasticMQDirectives with QueueURLModule with AttributesModule with TagsModule with SQSLimitsModule =>

  def createQueue(p: AnyParams) = {
    p.action(CreateQueue) {
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

            val secondsVisibilityTimeoutOpt = attributes.parseOptionalLong(VisibilityTimeoutParameter)
            val secondsDelayOpt = attributes.parseOptionalLong(DelaySecondsAttribute)
            val secondsReceiveMessageWaitTimeOpt = attributes.parseOptionalLong(ReceiveMessageWaitTimeSecondsAttribute)
            val isFifo = attributes.get("FifoQueue").contains("true")
            val hasContentBasedDeduplication = attributes.get("ContentBasedDeduplication").contains("true")

            val newQueueData = CreateQueueData(
              queueName,
              secondsVisibilityTimeoutOpt.map(sec => MillisVisibilityTimeout.fromSeconds(sec)),
              secondsDelayOpt.map(sec => Duration.standardSeconds(sec)),
              secondsReceiveMessageWaitTimeOpt.map(sec => Duration.standardSeconds(sec)),
              None,
              None,
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

            await(lookupOrCreateQueue(newQueueData))

            queueURL(queueName) { url =>
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

  private def lookupOrCreateQueue[T](newQueueData: CreateQueueData): Future[Unit] = {
    async {
      val createResult = await(queueManagerActor ? CreateQueueMsg(newQueueData))
      createResult match {
        case Left(e: ElasticMQError) =>
          throw new SQSException(e.code, errorMessage = Some(e.message))
        case Right(_) =>
      }
    }
  }
}
