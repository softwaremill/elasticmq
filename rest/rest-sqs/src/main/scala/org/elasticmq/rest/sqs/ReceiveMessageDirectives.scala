package org.elasticmq.rest.sqs

import org.elasticmq._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ReceiveMessages
import org.elasticmq.rest.sqs.Action.ReceiveMessage
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.MD5Util._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import org.joda.time.Duration
import spray.json.DefaultJsonProtocol.{StringJsonFormat, jsonFormat7}
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.xml.Elem

trait ReceiveMessageDirectives {
  this: ElasticMQDirectives with AttributesModule with SQSLimitsModule =>
  object MessageReadeableAttributeNames {
    val SentTimestampAttribute = "SentTimestamp"
    val ApproximateReceiveCountAttribute = "ApproximateReceiveCount"
    val ApproximateFirstReceiveTimestampAttribute =
      "ApproximateFirstReceiveTimestamp"
    val SenderIdAttribute = "SenderId"
    val MaxNumberOfMessagesAttribute = "MaxNumberOfMessages"
    val WaitTimeSecondsAttribute = "WaitTimeSeconds"
    val ReceiveRequestAttemptIdAttribute = "ReceiveRequestAttemptId"
    val MessageAttributeNamePattern = "MessageAttributeName(\\.\\d)?".r
    val MessageDeduplicationIdAttribute = "MessageDeduplicationId"
    val MessageGroupIdAttribute = "MessageGroupId"
    val AWSTraceHeaderAttribute = "AWSTraceHeader"
    val SequenceNumberAttribute = "SequenceNumber"

    val AllAttributeNames = SentTimestampAttribute :: ApproximateReceiveCountAttribute ::
      ApproximateFirstReceiveTimestampAttribute :: SenderIdAttribute :: MessageDeduplicationIdAttribute ::
      MessageGroupIdAttribute :: AWSTraceHeaderAttribute :: SequenceNumberAttribute :: Nil
  }

  def receiveMessage(p: RequestPayload, protocol: AWSProtocol) = {
    import MessageReadeableAttributeNames._

    p.action(ReceiveMessage) {
      val requestParameters = p.as[ReceiveMessageActionRequest]
      queueActorAndDataFromQueueUrl(requestParameters.QueueUrl) { (queueActor, queueData) =>
        val visibilityTimeoutParameterOpt: Option[Int] = requestParameters.VisibilityTimeout
        val maxNumberOfMessagesAttributeOpt: Option[Int] = requestParameters.MaxNumberOfMessages
        val waitTimeSecondsAttributeOpt: Option[Long] = requestParameters.WaitTimeSeconds

        val receiveRequestAttemptId = requestParameters.ReceiveRequestAttemptId match {
          // ReceiveRequestAttemptIdAttribute is only supported for FIFO queues
          case Some(v) if !queueData.isFifo =>
            throw SQSException.invalidQueueTypeParameter(v, ReceiveRequestAttemptIdAttribute)

          // Validate values
          case Some(attemptId) if !isValidFifoPropertyValue(attemptId) =>
            throw SQSException.invalidAlphanumericalPunctualParameterValue(attemptId, ReceiveRequestAttemptIdAttribute)

          // The docs at https://docs.aws.amazon.com/cli/latest/reference/sqs/receive-message.html quote:
          //   > If a caller of the receive-message action doesn't provide a ReceiveRequestAttemptId , Amazon SQS
          //   > generates a ReceiveRequestAttemptId .
          // That attempt id doesn't seem to be exposed anywhere however. For now, we will not generate an attempt id
          case a => a
        }

        val visibilityTimeoutFromParameters = visibilityTimeoutParameterOpt
          .map(MillisVisibilityTimeout.fromSeconds(_))
          .getOrElse(DefaultVisibilityTimeout)

        val maxNumberOfMessagesFromParameters =
          maxNumberOfMessagesAttributeOpt.getOrElse(1)

        val waitTimeSecondsFromParameters =
          waitTimeSecondsAttributeOpt.map(Duration.standardSeconds)

        val messageAttributeNames = requestParameters.MessageAttributeNames.getOrElse(List.empty)

        Limits
          .verifyNumberOfMessagesFromParameters(maxNumberOfMessagesFromParameters, sqsLimits)
          .fold(error => throw new SQSException(error), identity)

        waitTimeSecondsAttributeOpt.foreach(messageWaitTime =>
          Limits
            .verifyMessageWaitTime(messageWaitTime, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
        )

        val msgsFuture = queueActor ? ReceiveMessages(
          visibilityTimeoutFromParameters,
          maxNumberOfMessagesFromParameters,
          waitTimeSecondsFromParameters,
          receiveRequestAttemptId
        )

        val attributeNames = requestParameters.AttributeNames.getOrElse(List.empty)
        def calculateAttributeValues(msg: MessageData): List[(String, String)] = {
          import AttributeValuesCalculator.Rule

          possiblyEmptyAttributeValuesCalculator.calculate[String](
            attributeNames,
            Rule(SenderIdAttribute, () => Some("127.0.0.1")),
            Rule(SentTimestampAttribute, () => Some(msg.created.getMillis.toString)),
            Rule(ApproximateReceiveCountAttribute, () => Some(msg.statistics.approximateReceiveCount.toString)),
            Rule(MessageDeduplicationIdAttribute, () => msg.messageDeduplicationId.map(_.id)),
            Rule(MessageGroupIdAttribute, () => msg.messageGroupId),
            Rule(
              ApproximateFirstReceiveTimestampAttribute,
              () =>
                Some((msg.statistics.approximateFirstReceive match {
                  case NeverReceived            => 0
                  case OnDateTimeReceived(when) => when.getMillis
                }).toString)
            ),
            Rule(AWSTraceHeaderAttribute, () => msg.tracingId.map(_.id)),
            Rule(SequenceNumberAttribute, () => msg.sequenceNumber)
          )
        }

        def getFilteredAttributeNames(
            messageAttributeNames: Iterable[String],
            msg: MessageData
        ): Map[String, MessageAttribute] = {
          if (messageAttributeNames.exists(s => s == "All" || s == ".*")) {
            msg.messageAttributes
          } else {
            msg.messageAttributes
              .filterKeys(k => messageAttributeNames.exists(s => s == k || k.r.findFirstIn(s).isDefined))
              .toMap
          }
        }

        def messagesToXml(messages: List[MessageData]): List[Elem] = {
          messages.map { msg =>
            val receipt = msg.deliveryReceipt
              .map(_.receipt)
              .getOrElse(throw new RuntimeException("No receipt for a received msg."))
            val filteredMessageAttributes = getFilteredAttributeNames(messageAttributeNames, msg)
            <Message>
              <MessageId>{msg.id.id}</MessageId>
              <ReceiptHandle>{receipt}</ReceiptHandle>
              <MD5OfBody>{md5Digest(msg.content)}</MD5OfBody>
              <Body>{XmlUtil.convertTexWithCRToNodeSeq(msg.content)}</Body>
              {attributesToXmlConverter.convert(calculateAttributeValues(msg))}
              {
              if (filteredMessageAttributes.nonEmpty) <MD5OfMessageAttributes>{
                md5AttributeDigest(filteredMessageAttributes)
              }</MD5OfMessageAttributes>
            }
              {messageAttributesToXmlConverter.convert(filteredMessageAttributes.toList)}
            </Message>
          }
        }

        def messagesToJson(messages: List[MessageData]): List[Message] =
          messages.map { message =>
            val receipt = message.deliveryReceipt
              .map(_.receipt)
              .getOrElse(throw new RuntimeException("No receipt for a received msg."))
            val filteredMessageAttributes = getFilteredAttributeNames(messageAttributeNames, message)

            Message(
              Attributes = calculateAttributeValues(message).toMap,
              Body = message.content,
              MD5OfBody = md5Digest(message.content),
              MD5OfMessageAttributes =
                if (filteredMessageAttributes.nonEmpty) Some(md5AttributeDigest(filteredMessageAttributes)) else None,
              MessageAttributes = filteredMessageAttributes,
              MessageId = message.id.id,
              ReceiptHandle = receipt
            )
          }

        msgsFuture.map { messages =>
          protocol match {
            case AWSProtocol.AWSQueryProtocol =>
              respondWith {
                <ReceiveMessageResponse>
                  {
                  if (messages.isEmpty)
                    <ReceiveMessageResult/>
                  else
                    <ReceiveMessageResult>
                    {messagesToXml(messages)}
                  </ReceiveMessageResult>
                }<ResponseMetadata>
                  <RequestId>
                    {EmptyRequestId}
                  </RequestId>
                </ResponseMetadata>
                </ReceiveMessageResponse>
              }
            case _ => {
              val jsonMessages: List[Message] = messagesToJson(messages)
              complete(ReceiveMessageResponse(jsonMessages))
            }
          }
        }
      }
    }
  }

  case class ReceiveMessageActionRequest(
      AttributeNames: Option[List[String]],
      MaxNumberOfMessages: Option[Int],
      MessageAttributeNames: Option[List[String]],
      QueueUrl: String,
      ReceiveRequestAttemptId: Option[String],
      VisibilityTimeout: Option[Int],
      WaitTimeSeconds: Option[Long]
  )

  object ReceiveMessageActionRequest {
    def apply(
        AttributeNames: Option[List[String]],
        MaxNumberOfMessages: Option[Int],
        MessageAttributeNames: Option[List[String]],
        QueueUrl: String,
        ReceiveRequestAttemptId: Option[String],
        VisibilityTimeout: Option[Int],
        WaitTimeSeconds: Option[Long]
    ): ReceiveMessageActionRequest = {
      new ReceiveMessageActionRequest(
        AttributeNames =
          AttributeNames.map(atr => if (atr.contains("All")) MessageReadeableAttributeNames.AllAttributeNames else atr),
        MaxNumberOfMessages = MaxNumberOfMessages,
        MessageAttributeNames = MessageAttributeNames,
        QueueUrl = QueueUrl,
        ReceiveRequestAttemptId = ReceiveRequestAttemptId,
        VisibilityTimeout = VisibilityTimeout,
        WaitTimeSeconds = WaitTimeSeconds
      )
    }

    implicit val requestJsonFormat: RootJsonFormat[ReceiveMessageActionRequest] = jsonFormat7(
      ReceiveMessageActionRequest.apply
    )

    implicit val requestParamReader: FlatParamsReader[ReceiveMessageActionRequest] =
      new FlatParamsReader[ReceiveMessageActionRequest] {
        override def read(params: Map[String, String]): ReceiveMessageActionRequest = {
          val attributeNames = attributeNamesReader.read(params, MessageReadeableAttributeNames.AllAttributeNames)
          val maxNumberOfMessages = params.get(MessageReadeableAttributeNames.MaxNumberOfMessagesAttribute).map(_.toInt)
          val messageAttributeNames = getMessageAttributeNames(params).toList
          val queueUrl = requiredParameter(params)(QueueUrlParameter)
          val receiveRequestAttemptId = params.get(MessageReadeableAttributeNames.ReceiveRequestAttemptIdAttribute)
          val visibilityTimeout = params.get(VisibilityTimeoutParameter).map(_.toInt)
          val waitTimeSeconds = params.get(MessageReadeableAttributeNames.WaitTimeSecondsAttribute).map(_.toLong)
          ReceiveMessageActionRequest(
            Some(attributeNames),
            maxNumberOfMessages,
            Some(messageAttributeNames),
            queueUrl,
            receiveRequestAttemptId,
            visibilityTimeout,
            waitTimeSeconds
          )
        }
      }

    def getMessageAttributeNames(p: Map[String, String]): Iterable[String] = {
      p.filterKeys(k =>
        MessageReadeableAttributeNames.MessageAttributeNamePattern
          .findFirstIn(k)
          .isDefined
      ).values
    }
  }

  case class ReceiveMessageResponse(Messages: List[Message])
  object ReceiveMessageResponse {
    implicit val responseJsonFormat: RootJsonFormat[ReceiveMessageResponse] = jsonFormat1(ReceiveMessageResponse.apply)
  }

  case class Message(
      Attributes: Map[String, String],
      Body: String,
      MD5OfBody: String,
      MD5OfMessageAttributes: Option[String],
      MessageAttributes: Map[String, MessageAttribute],
      MessageId: String,
      ReceiptHandle: String
  )
  object Message extends MessageAttributesSupport {
    implicit val responseJsonFormat: RootJsonFormat[Message] = jsonFormat7(Message.apply)
  }

}
