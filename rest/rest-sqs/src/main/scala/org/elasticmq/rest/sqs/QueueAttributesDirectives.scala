package org.elasticmq.rest.sqs

import akka.http.scaladsl.server.Route
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout}
import org.joda.time.Duration
import spray.json.JsonParser.ParsingException
import spray.json._

import scala.async.Async.{await, _}
import scala.concurrent.Future

trait QueueAttributesDirectives {
  this: ElasticMQDirectives with AttributesModule =>

  object QueueWriteableAttributeNames {
    val AllWriteableAttributeNames: List[String] = VisibilityTimeoutParameter :: DelaySecondsAttribute ::
      ReceiveMessageWaitTimeSecondsAttribute :: RedrivePolicyParameter :: Nil
  }

  object UnsupportedAttributeNames {
    val PolicyAttribute = "Policy"
    val MaximumMessageSizeAttribute = "MaximumMessageSize"
    val MessageRetentionPeriodAttribute = "MessageRetentionPeriod"

    val AllUnsupportedAttributeNames: List[String] = PolicyAttribute :: MaximumMessageSizeAttribute ::
      MessageRetentionPeriodAttribute :: Nil
  }

  object FifoAttributeNames {
    val ContentBasedDeduplication = "ContentBasedDeduplication"
    val FifoQueue = "FifoQueue"

    val AllFifoAttributeNames: Seq[String] = Seq(
      ContentBasedDeduplication,
      FifoQueue
    )
  }

  object QueueReadableAttributeNames {
    val ApproximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"
    val ApproximateNumberOfMessagesNotVisibleAttribute =
      "ApproximateNumberOfMessagesNotVisible"
    val ApproximateNumberOfMessagesDelayedAttribute =
      "ApproximateNumberOfMessagesDelayed"
    val CreatedTimestampAttribute = "CreatedTimestamp"
    val LastModifiedTimestampAttribute = "LastModifiedTimestamp"

    val AllAttributeNames: List[String] = QueueWriteableAttributeNames.AllWriteableAttributeNames ++
      (ApproximateNumberOfMessagesAttribute ::
        ApproximateNumberOfMessagesNotVisibleAttribute ::
        ApproximateNumberOfMessagesDelayedAttribute ::
        CreatedTimestampAttribute ::
        LastModifiedTimestampAttribute ::
        QueueArnAttribute :: Nil) ++ FifoAttributeNames.AllFifoAttributeNames
  }

  def getQueueAttributes(p: AnyParams): Route = {
    p.action("GetQueueAttributes") {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        import QueueReadableAttributeNames._
        import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

        def calculateAttributeValues(attributeNames: List[String]): List[(String, Future[String])] = {
          lazy val stats = queueActor ? GetQueueStatistics(System.currentTimeMillis())

          import AttributeValuesCalculator.Rule

          val alwaysAvailableParameterRules = Seq(
            Rule(VisibilityTimeoutParameter,
                 () => Future.successful(queueData.defaultVisibilityTimeout.seconds.toString)),
            Rule(DelaySecondsAttribute, () => Future.successful(queueData.delay.getStandardSeconds.toString)),
            Rule(ApproximateNumberOfMessagesAttribute, () => stats.map(_.approximateNumberOfVisibleMessages.toString)),
            Rule(ApproximateNumberOfMessagesNotVisibleAttribute,
                 () => stats.map(_.approximateNumberOfInvisibleMessages.toString)),
            Rule(ApproximateNumberOfMessagesDelayedAttribute,
                 () => stats.map(_.approximateNumberOfMessagesDelayed.toString)),
            Rule(CreatedTimestampAttribute, () => Future.successful((queueData.created.getMillis / 1000L).toString)),
            Rule(LastModifiedTimestampAttribute,
                 () => Future.successful((queueData.lastModified.getMillis / 1000L).toString)),
            Rule(ReceiveMessageWaitTimeSecondsAttribute,
                 () => Future.successful(queueData.receiveMessageWait.getStandardSeconds.toString)),
            Rule(QueueArnAttribute, () => Future.successful("arn:aws:sqs:elasticmq:000000000000:" + queueData.name))
          )

          val optionalRules = Seq(
            queueData.deadLettersQueue
              .map(dlq => RedrivePolicy(dlq.name, dlq.maxReceiveCount))
              .map(redrivePolicy =>
                Rule(RedrivePolicyParameter, () => Future.successful(redrivePolicy.toJson.toString)))
          )

          val fifoRules = if (queueData.isFifo) {
            Seq(
              Rule(FifoAttributeNames.FifoQueue, () => Future.successful(queueData.isFifo.toString)),
              Rule(
                FifoAttributeNames.ContentBasedDeduplication,
                () => Future.successful(queueData.hasContentBasedDeduplication.toString)
              )
            )
          } else {
            Seq()
          }

          val rules = alwaysAvailableParameterRules ++ optionalRules.flatten ++ fifoRules
          attributeValuesCalculator.calculate(attributeNames, rules: _*)
        }

        def responseXml(attributes: List[(String, String)]) = {
          <GetQueueAttributesResponse>
            <GetQueueAttributesResult>
              {attributesToXmlConverter.convert(attributes)}
            </GetQueueAttributesResult>
            <ResponseMetadata>
              <RequestId>{EmptyRequestId}</RequestId>
            </ResponseMetadata>
          </GetQueueAttributesResponse>
        }

        val attributeNames = attributeNamesReader.read(p, AllAttributeNames)
        val attributesFuture =
          Future.sequence(calculateAttributeValues(attributeNames).map(p => p._2.map((p._1, _))))

        attributesFuture.map { attributes =>
          respondWith {
            responseXml(attributes)
          }
        }
      }
    }
  }

  def setQueueAttributes(p: AnyParams): Route = {
    p.action("SetQueueAttributes") {
      queueActorFromRequest(p) { queueActor =>
        val attributes = attributeNameAndValuesReader.read(p)

        val result = attributes.map({
          case (attributeName, attributeValue) =>
            attributeName match {
              case VisibilityTimeoutParameter =>
                queueActor ? UpdateQueueDefaultVisibilityTimeout(
                  MillisVisibilityTimeout.fromSeconds(attributeValue.toLong))
              case DelaySecondsAttribute =>
                queueActor ? UpdateQueueDelay(Duration.standardSeconds(attributeValue.toLong))
              case ReceiveMessageWaitTimeSecondsAttribute =>
                queueActor ? UpdateQueueReceiveMessageWait(Duration.standardSeconds(attributeValue.toLong))
              case RedrivePolicyParameter =>
                val redrivePolicy =
                  try {
                    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._
                    attributeValue.parseJson.convertTo[RedrivePolicy]
                  } catch {
                    case e: DeserializationException =>
                      logger.warn("Cannot deserialize the redrive policy attribute", e)
                      throw new SQSException("MalformedQueryString")
                    case e: ParsingException =>
                      logger.warn("Cannot parse the redrive policy attribute", e)
                      throw new SQSException("MalformedQueryString")
                  }
                async {
                  val deadLettersQueueActor = await(queueManagerActor ? LookupQueue(redrivePolicy.queueName))
                  if (deadLettersQueueActor.isEmpty) {
                    throw SQSException.nonExistentQueue
                  }

                  if (redrivePolicy.maxReceiveCount < 1 || redrivePolicy.maxReceiveCount > 1000) {
                    throw SQSException.invalidParameterValue
                  }
                  queueActor ? UpdateQueueDeadLettersQueue(
                    Some(DeadLettersQueueData(redrivePolicy.queueName, redrivePolicy.maxReceiveCount)),
                    deadLettersQueueActor)
                }
              case attr
                  if UnsupportedAttributeNames.AllUnsupportedAttributeNames
                    .contains(attr) =>
                logger.warn("Ignored attribute \"" + attr + "\" (supported by SQS but not ElasticMQ)")
                Future.successful(())
              case attr =>
                logger.warn("Unsupported attribute \"" + attr + "\" (failing on ElasticMQ)")
                Future.failed(new SQSException("InvalidAttributeName"))
            }
        })

        Future.sequence(result).map { _ =>
          respondWith {
            <SetQueueAttributesResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </SetQueueAttributesResponse>
          }
        }
      }
    }
  }
}
