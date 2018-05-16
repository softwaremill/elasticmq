package org.elasticmq.rest.sqs

import org.elasticmq.MillisVisibilityTimeout
import Constants._
import org.joda.time.Duration
import org.elasticmq.msg.{
  GetQueueStatistics,
  UpdateQueueDefaultVisibilityTimeout,
  UpdateQueueDelay,
  UpdateQueueReceiveMessageWait
}
import org.elasticmq.actor.reply._
import scala.concurrent.Future

import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RedrivePolicy
import spray.json._

trait QueueAttributesDirectives {
  this: ElasticMQDirectives with AttributesModule =>
  object QueueWriteableAttributeNames {
    val AllWriteableAttributeNames = VisibilityTimeoutParameter :: DelaySecondsAttribute ::
      ReceiveMessageWaitTimeSecondsAttribute :: RedrivePolicyParameter :: Nil
  }

  object UnsupportedAttributeNames {
    val PolicyAttribute = "Policy"
    val MaximumMessageSizeAttribute = "MaximumMessageSize"
    val MessageRetentionPeriodAttribute = "MessageRetentionPeriod"
    val RedrivePolicyAttribute = "RedrivePolicy"

    val AllUnsupportedAttributeNames = PolicyAttribute :: MaximumMessageSizeAttribute ::
      MessageRetentionPeriodAttribute :: RedrivePolicyAttribute :: Nil
  }

  object QueueReadableAttributeNames {
    val ApproximateNumberOfMessagesAttribute = "ApproximateNumberOfMessages"
    val ApproximateNumberOfMessagesNotVisibleAttribute =
      "ApproximateNumberOfMessagesNotVisible"
    val ApproximateNumberOfMessagesDelayedAttribute =
      "ApproximateNumberOfMessagesDelayed"
    val CreatedTimestampAttribute = "CreatedTimestamp"
    val LastModifiedTimestampAttribute = "LastModifiedTimestamp"

    val AllAttributeNames = QueueWriteableAttributeNames.AllWriteableAttributeNames ++
      (ApproximateNumberOfMessagesAttribute ::
        ApproximateNumberOfMessagesNotVisibleAttribute ::
        ApproximateNumberOfMessagesDelayedAttribute ::
        CreatedTimestampAttribute ::
        LastModifiedTimestampAttribute ::
        QueueArnAttribute :: Nil)
  }

  def getQueueAttributes(p: AnyParams) = {
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
          val rules = alwaysAvailableParameterRules ++ optionalRules.flatten

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

  def setQueueAttributes(p: AnyParams) = {
    p.action("SetQueueAttributes") {
      queueActorFromRequest(p) { queueActor =>
        val attributes = attributeNameAndValuesReader.read(p)

        val result = attributes.map({
          case (attributeName, attributeValue) =>
            attributeName match {
              case VisibilityTimeoutParameter => {
                queueActor ? UpdateQueueDefaultVisibilityTimeout(
                  MillisVisibilityTimeout.fromSeconds(attributeValue.toLong))
              }
              case DelaySecondsAttribute => {
                queueActor ? UpdateQueueDelay(Duration.standardSeconds(attributeValue.toLong))
              }
              case ReceiveMessageWaitTimeSecondsAttribute => {
                queueActor ? UpdateQueueReceiveMessageWait(Duration.standardSeconds(attributeValue.toLong))
              }
              case attr
                  if UnsupportedAttributeNames.AllUnsupportedAttributeNames
                    .contains(attr) => {
                logger.warn("Ignored attribute \"" + attr + "\" (supported by SQS but not ElasticMQ)")
                Future.successful(())
              }
              case _ => Future.failed(new SQSException("InvalidAttributeName"))
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
