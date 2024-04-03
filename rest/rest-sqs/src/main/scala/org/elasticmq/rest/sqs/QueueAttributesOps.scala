package org.elasticmq.rest.sqs

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.util.Timeout
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicy.BackwardCompatibleRedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson._
import org.elasticmq.util.Logging
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import spray.json.JsonParser.ParsingException
import spray.json._

import java.time.Duration
import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

trait QueueAttributesOps extends AttributesModule with AwsConfiguration {
  this: Logging =>

  val attributeValuesCalculator = new AttributeValuesCalculator

  def getAllQueueAttributes(queueActor: ActorRef, queueData: QueueData)(implicit
      timeout: Timeout,
      executionContext: ExecutionContext
  ): Future[List[(String, String)]] = {
    getQueueAttributes(QueueReadableAttributeNames.AllAttributeNames, queueActor, queueData)
  }

  def getQueueAttributes(attributeNames: List[String], queueActor: ActorRef, queueData: QueueData)(implicit
      timeout: Timeout,
      executionContext: ExecutionContext
  ): Future[List[(String, String)]] = {
    import QueueReadableAttributeNames._
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    def calculateAttributeValues(attributeNames: List[String]): List[(String, Future[String])] = {
      lazy val stats = queueActor ? GetQueueStatistics(System.currentTimeMillis())

      val alwaysAvailableParameterRules = Seq(
        AttributeValuesCalculator.Rule(
          VisibilityTimeoutParameter,
          () => Future.successful(queueData.defaultVisibilityTimeout.seconds.toString)
        ),
        AttributeValuesCalculator
          .Rule(DelaySecondsAttribute, () => Future.successful(queueData.delay.getSeconds.toString)),
        AttributeValuesCalculator
          .Rule(ApproximateNumberOfMessagesAttribute, () => stats.map(_.approximateNumberOfVisibleMessages.toString)),
        AttributeValuesCalculator.Rule(
          ApproximateNumberOfMessagesNotVisibleAttribute,
          () => stats.map(_.approximateNumberOfInvisibleMessages.toString)
        ),
        AttributeValuesCalculator.Rule(
          ApproximateNumberOfMessagesDelayedAttribute,
          () => stats.map(_.approximateNumberOfMessagesDelayed.toString)
        ),
        AttributeValuesCalculator
          .Rule(
            CreatedTimestampAttribute,
            () => Future.successful((queueData.created.toInstant.toEpochMilli / 1000L).toString)
          ),
        AttributeValuesCalculator.Rule(
          LastModifiedTimestampAttribute,
          () => Future.successful((queueData.lastModified.toInstant.toEpochMilli / 1000L).toString)
        ),
        AttributeValuesCalculator.Rule(
          ReceiveMessageWaitTimeSecondsAttribute,
          () => Future.successful(queueData.receiveMessageWait.getSeconds.toString)
        ),
        AttributeValuesCalculator.Rule(
          QueueArnAttribute,
          () => Future.successful(getArn(queueData.name))
        )
      )

      val optionalRules = Seq(
        queueData.deadLettersQueue
          .map(dlq => RedrivePolicy(dlq.name, awsRegion, awsAccountId, dlq.maxReceiveCount))
          .map(redrivePolicy =>
            AttributeValuesCalculator
              .Rule(RedrivePolicyParameter, () => Future.successful(redrivePolicy.toJson.toString))
          )
      )

      val fifoRules = if (queueData.isFifo) {
        Seq(
          AttributeValuesCalculator
            .Rule(FifoAttributeNames.FifoQueue, () => Future.successful(queueData.isFifo.toString)),
          AttributeValuesCalculator.Rule(
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

    Future.sequence(calculateAttributeValues(attributeNames).map(p => p._2.map((p._1, _))))
  }

  def setQueueAttributes(attributes: Map[String, String], queueActor: ActorRef, queueManagerActor: ActorRef)(implicit
      timeout: Timeout,
      executionContext: ExecutionContext
  ) = {
    attributes.map({ case (attributeName, attributeValue) =>
      attributeName match {
        case VisibilityTimeoutParameter =>
          queueActor ? UpdateQueueDefaultVisibilityTimeout(
            MillisVisibilityTimeout.fromSeconds(attributeValue.toLong)
          )
        case DelaySecondsAttribute =>
          queueActor ? UpdateQueueDelay(Duration.ofSeconds(attributeValue.toLong))
        case ReceiveMessageWaitTimeSecondsAttribute =>
          queueActor ? UpdateQueueReceiveMessageWait(Duration.ofSeconds(attributeValue.toLong))

        case RedrivePolicyParameter =>
          val redrivePolicy =
            try {
              attributeValue.parseJson.convertTo[BackwardCompatibleRedrivePolicy]
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
              deadLettersQueueActor
            )
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
  }

}

object QueueWriteableAttributeNames {
  val AllWriteableAttributeNames: List[String] = VisibilityTimeoutParameter :: DelaySecondsAttribute ::
    ReceiveMessageWaitTimeSecondsAttribute :: RedrivePolicyParameter :: Nil
}

object UnsupportedAttributeNames {
  val PolicyAttribute = "Policy"
  val MaximumMessageSizeAttribute = "MaximumMessageSize"
  val MessageRetentionPeriodAttribute = "MessageRetentionPeriod"
  val FifoQueueAttribute = "FifoQueue"

  val AllUnsupportedAttributeNames: List[String] = PolicyAttribute :: MaximumMessageSizeAttribute ::
    MessageRetentionPeriodAttribute :: FifoQueueAttribute :: Nil
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
