package org.elasticmq.rest.sqs

import org.elasticmq._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{LookupQueue, CreateQueue => CreateQueueMsg}
import org.elasticmq.rest.sqs.Action.CreateQueue
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.ParametersUtil._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RedrivePolicy.BackwardCompatibleRedrivePolicy
import org.elasticmq.rest.sqs.model.RequestPayload
import org.joda.time.Duration
import spray.json.DefaultJsonProtocol._
import spray.json.JsonParser.ParsingException
import spray.json._

import scala.async.Async._
import scala.concurrent.Future
import scala.xml.Elem

trait CreateQueueDirectives {
  this: ElasticMQDirectives with QueueURLModule with SQSLimitsModule with AkkaSupport =>

  def createQueue(p: RequestPayload)(implicit protocol: AWSProtocol, xmlNsVersion: XmlNsVersion) = {
    p.action(CreateQueue) {
      rootPath {

        val requestParams = p.as[CreateQueueActionRequest]
        val attributes = requestParams.Attributes.getOrElse(Map.empty)

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
            requestParams.QueueName,
            secondsVisibilityTimeoutOpt.map(sec => MillisVisibilityTimeout.fromSeconds(sec)),
            secondsDelayOpt.map(sec => Duration.standardSeconds(sec)),
            secondsReceiveMessageWaitTimeOpt.map(sec => Duration.standardSeconds(sec)),
            None,
            None,
            redrivePolicy.map(rd => DeadLettersQueueData(rd.queueName, rd.maxReceiveCount)),
            isFifo,
            hasContentBasedDeduplication,
            tags = requestParams.tags.getOrElse(Map.empty)
          )

          secondsReceiveMessageWaitTimeOpt.foreach(messageWaitTime =>
            Limits
              .verifyMessageWaitTime(messageWaitTime, sqsLimits)
              .fold(error => throw new SQSException(error), identity)
          )

          await(lookupOrCreateQueue(newQueueData))

          queueURL(requestParams.QueueName) { url => complete(CreateQueueResponse(url)) }
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

case class CreateQueueActionRequest(
    QueueName: String,
    Attributes: Option[Map[String, String]],
    tags: Option[Map[String, String]]
)

object CreateQueueActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[CreateQueueActionRequest] = jsonFormat3(CreateQueueActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[CreateQueueActionRequest] =
    new FlatParamsReader[CreateQueueActionRequest] {
      override def read(params: Map[String, String]): CreateQueueActionRequest = {
        val attributes = AttributesModule.attributeNameAndValuesReader.read(params)
        val tags = TagsModule.tagNameAndValuesReader.read(params)
        val queueName = requiredParameter(params)(QueueNameParameter)
        CreateQueueActionRequest(queueName, Some(attributes), Some(tags))
      }
    }
}

case class CreateQueueResponse(QueueUrl: String)

object CreateQueueResponse {
  implicit val format: RootJsonFormat[CreateQueueResponse] = jsonFormat1(CreateQueueResponse.apply)

  implicit val xmlSerializer: XmlSerializer[CreateQueueResponse] = new XmlSerializer[CreateQueueResponse] {
    override def toXml(t: CreateQueueResponse): Elem =
      <CreateQueueResponse>
        <CreateQueueResult>
          <QueueUrl>{t.QueueUrl}</QueueUrl>
        </CreateQueueResult>
        <ResponseMetadata>
          <RequestId>{EmptyRequestId}</RequestId>
        </ResponseMetadata>
      </CreateQueueResponse>
  }
}
