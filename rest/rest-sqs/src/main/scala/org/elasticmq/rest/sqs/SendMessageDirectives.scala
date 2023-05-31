package org.elasticmq.rest.sqs

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import org.elasticmq._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.SendMessage
import org.elasticmq.rest.sqs.Action.{SendMessage => SendMessageAction}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.MD5Util._
import org.elasticmq.rest.sqs.ParametersUtil._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.Future
import scala.xml.Elem

trait SendMessageDirectives {
  this: ElasticMQDirectives with SQSLimitsModule with ResponseMarshaller =>
  private val messageSystemAttributeNamePattern = """MessageSystemAttribute\.(\d+)\.Name""".r

  def sendMessage(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(SendMessageAction) {
      val params = p.as[SendMessageActionRequest]

      queueActorAndDataFromQueueUrl(params.QueueUrl) { (queueActor, queueData) =>
        val message = createMessage(params, queueData, orderIndex = 0, p.xRayTracingHeader)

        validateMessageAttributes(params.MessageAttributes.getOrElse(Map.empty))

        doSendMessage(queueActor, message).map { case (message, digest, messageAttributeDigest) =>
          complete(SendMessageResponse(messageAttributeDigest, digest, None, message.id.id, message.sequenceNumber))
        }
      }
    }
  }

  def getMessageAttributes(parameters: Map[String, String]): Map[String, MessageAttribute] = {
    // Determine number of attributes -- there are likely ways to improve this
    val numAttributes = parameters
      .map { case (k, _) =>
        if (k.startsWith("MessageAttribute.")) {
          k.split("\\.")(1).toInt
        } else {
          0
        }
      }
      .toList
      .union(List(0))
      .max // even if nothing, return 0

    (1 to numAttributes).map { i =>
      val name = parameters("MessageAttribute." + i + ".Name")
      val dataType = parameters("MessageAttribute." + i + ".Value.DataType")

      val primaryDataType = dataType.split('.')(0)
      val customDataType = if (dataType.contains('.')) {
        Some(dataType.substring(dataType.indexOf('.') + 1))
      } else {
        None
      }

      val value: MessageAttribute = primaryDataType match {
        case "String" =>
          val strValue = parameters("MessageAttribute." + i + ".Value.StringValue")
          StringMessageAttribute(strValue, customDataType)
        case "Number" =>
          val strValue = parameters("MessageAttribute." + i + ".Value.StringValue")
          NumberMessageAttribute(strValue, customDataType)
        case "Binary" =>
          BinaryMessageAttribute.fromBase64(parameters("MessageAttribute." + i + ".Value.BinaryValue"), customDataType)
        case "" =>
          throw new SQSException(s"Attribute '$name' must contain a non-empty attribute type")
        case _ =>
          throw new Exception("Currently only handles String, Number and Binary typed attributes")
      }

      (name, value)
    }.toMap
  }

  def getMessageSystemAttributes(parameters: Map[String, String]): Map[String, MessageAttribute] = {
    parameters.flatMap {
      case (messageSystemAttributeNamePattern(index), parameterName) =>
        val parameterDataType = parameters(s"MessageSystemAttribute.$index.Value.DataType")
        val parameterValue = parameterDataType match {
          case "String" => StringMessageAttribute(parameters(s"MessageSystemAttribute.$index.Value.StringValue"))
          case "Number" => NumberMessageAttribute(parameters(s"MessageSystemAttribute.$index.Value.StringValue"))
          case "Binary" =>
            BinaryMessageAttribute.fromBase64(parameters(s"MessageAttribute.$index.Value.BinaryValue"))
        }
        Some((parameterName, parameterValue))
      case _ => None
    }
  }

  def createMessage(
      parameters: SendMessageActionRequest,
      queueData: QueueData,
      orderIndex: Int,
      xRayTracingHeder: Option[String]
  ): NewMessageData = {
    val body = parameters.MessageBody
    val messageAttributes = parameters.MessageAttributes.getOrElse(Map.empty)
    val messageSystemAttributes = parameters.MessageSystemAttributes.getOrElse(Map.empty)

    Limits.verifyMessageBody(body, sqsLimits).fold(error => throw new SQSException(error), identity)

    val messageGroupId = parameters.MessageGroupId match {
      // MessageGroupId is only supported for FIFO queues
      case Some(v) if !queueData.isFifo => throw SQSException.invalidQueueTypeParameter(v, MessageGroupIdParameter)

      // MessageGroupId is required for FIFO queues
      case None if queueData.isFifo => throw SQSException.missingParameter(MessageGroupIdParameter)

      // Ensure the given value is valid
      case Some(id) if !isValidFifoPropertyValue(id) =>
        throw SQSException.invalidAlphanumericalPunctualParameterValue(id, MessageGroupIdParameter)

      // This must be a correct value (or this isn't a FIFO queue and no value is required)
      case m => m
    }

    val messageDeduplicationId = parameters.MessageDeduplicationId match {
      // MessageDeduplicationId is only supported for FIFO queues
      case Some(v) if !queueData.isFifo =>
        throw SQSException.invalidQueueTypeParameter(v, MessageDeduplicationIdParameter)

      // Ensure the given value is valid
      case Some(id) if !isValidFifoPropertyValue(id) =>
        throw SQSException.invalidAlphanumericalPunctualParameterValue(id, MessageDeduplicationIdParameter)

      // If a valid message group id is provided, use it, as it takes priority over the queue's content based deduping
      case Some(id) => Some(DeduplicationId(id))

      // MessageDeduplicationId is required for FIFO queues that don't have content based deduplication
      case None if queueData.isFifo && !queueData.hasContentBasedDeduplication =>
        throw new SQSException(
          InvalidParameterValueErrorName,
          errorMessage = Some(
            s"The queue should either have ContentBasedDeduplication enabled or $MessageDeduplicationIdParameter provided explicitly"
          )
        )

      // If no MessageDeduplicationId was provided and content based deduping is enabled for queue, generate one
      case None if queueData.isFifo && queueData.hasContentBasedDeduplication =>
        Some(DeduplicationId.fromMessageBody(body))

      // This must be a non-FIFO queue that doesn't require a dedup id
      case None => None
    }

    val delaySecondsOption = parameters.DelaySeconds match {
      case Some(v) if v < 0 || v > 900 =>
        // Messages can at most be delayed for 15 minutes
        throw SQSException.invalidParameter(
          v.toString,
          DelaySecondsParameter,
          Some("DelaySeconds must be >= 0 and <= 900")
        )
      case Some(v) if v > 0 && queueData.isFifo =>
        // FIFO queues don't support delays
        throw SQSException.invalidQueueTypeParameter(v.toString, DelaySecondsParameter)
      case d => d
    }

    val nextDelivery = delaySecondsOption match {
      case None               => ImmediateNextDelivery
      case Some(delaySeconds) => AfterMillisNextDelivery(delaySeconds * 1000)
    }

    val maybeTracingId = messageSystemAttributes
      .get(AwsTraceHeaderSystemAttribute)
      .map {
        case StringMessageAttribute(value, _) => TracingId(value)
        case NumberMessageAttribute(_, _) =>
          throw new SQSException(
            InvalidParameterValueErrorName,
            errorMessage = Some(
              s"$AwsTraceHeaderSystemAttribute should be declared as a String, instead it was recognized as a Number"
            )
          )
        case BinaryMessageAttribute(_, _) =>
          throw new SQSException(
            InvalidParameterValueErrorName,
            errorMessage = Some(
              s"$AwsTraceHeaderSystemAttribute should be declared as a String, instead it was recognized as a Binary value"
            )
          )
      }
      .orElse(xRayTracingHeder.map(TracingId.apply))

    NewMessageData(
      None,
      body,
      messageAttributes,
      nextDelivery,
      messageGroupId,
      messageDeduplicationId,
      orderIndex,
      maybeTracingId,
      None
    )
  }

  def doSendMessage(
      queueActor: ActorRef,
      message: NewMessageData
  ): Future[(MessageData, String, Option[String])] = {
    val digest = md5Digest(message.content)

    val messageAttributeDigest = if (message.messageAttributes.isEmpty) {
      None
    } else {
      Some(md5AttributeDigest(message.messageAttributes))
    }

    for {
      message <- queueActor ? SendMessage(message)
    } yield (message, digest, messageAttributeDigest)
  }

  def verifyMessageNotTooLong(messageLength: Int): Unit =
    Limits.verifyMessageLength(messageLength, sqsLimits).fold(error => throw new SQSException(error), identity)

  def validateMessageAttributes(messageAttributes: Map[String, MessageAttribute]): Unit = {

    messageAttributes.foreach { case (name, value) =>
      Limits
        .verifyMessageAttributesNumber(messageAttributes.size, sqsLimits)
        .fold(error => throw new SQSException(error), identity)

      val availableDataTypes = Set("String", "Number", "Binary")
      if (value.getDataType().isEmpty)
        throw throw new SQSException(s"Attribute '$name' must contain a non-empty attribute type")
      if (!availableDataTypes.contains(value.getDataType()))
        throw new Exception("Currently only handles String, Number and Binary typed attributes")

      value match {
        case StringMessageAttribute(stringValue, _) =>
          Limits
            .verifyMessageStringAttribute(name, stringValue, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
        case NumberMessageAttribute(stringValue, _) =>
          Limits
            .verifyMessageNumberAttribute(stringValue, name, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
        case BinaryMessageAttribute(_, _) => ()
      }
    }
  }

  case class SendMessageActionRequest(
      DelaySeconds: Option[Long],
      MessageBody: String,
      MessageDeduplicationId: Option[String],
      MessageGroupId: Option[String],
      MessageSystemAttributes: Option[Map[String, MessageAttribute]],
      MessageAttributes: Option[Map[String, MessageAttribute]],
      QueueUrl: String
  )

  object SendMessageActionRequest extends MessageAttributesSupport {

    implicit val jsonFormat: RootJsonFormat[SendMessageActionRequest] = jsonFormat7(SendMessageActionRequest.apply)

    implicit val queryFormat: FlatParamsReader[SendMessageActionRequest] =
      new FlatParamsReader[SendMessageActionRequest] {
        override def read(params: Map[String, String]): SendMessageActionRequest = {
          SendMessageActionRequest(
            DelaySeconds = params.parseOptionalLong(DelaySecondsParameter),
            MessageBody = requiredParameter(params)(MessageBodyParameter),
            MessageDeduplicationId = params.get(MessageDeduplicationIdParameter),
            MessageGroupId = params.get(MessageGroupIdParameter),
            MessageSystemAttributes = Some(getMessageSystemAttributes(params)),
            MessageAttributes = Some(getMessageAttributes(params)),
            QueueUrl = requiredParameter(params)(QueueUrlParameter)
          )
        }
      }
  }
}

case class SendMessageResponse(
    MD5OfMessageAttributes: Option[String],
    MD5OfMessageBody: String,
    MD5OfMessageSystemAttributes: Option[String],
    MessageId: String,
    SequenceNumber: Option[String]
)

object SendMessageResponse {
  implicit val jsonFormat: RootJsonFormat[SendMessageResponse] = jsonFormat5(SendMessageResponse.apply)

  implicit val xmlSerializer: XmlSerializer[SendMessageResponse] = new XmlSerializer[SendMessageResponse] {
    override def toXml(t: SendMessageResponse): Elem =
      <SendMessageResponse>
          <SendMessageResult>
            {t.MD5OfMessageAttributes.map(d => <MD5OfMessageAttributes>{d}</MD5OfMessageAttributes>).getOrElse(())}
            <MD5OfMessageBody>{t.MD5OfMessageBody}</MD5OfMessageBody>
            <MessageId>{t.MessageId}</MessageId>
            {t.SequenceNumber.map(x => <SequenceNumber>{x}</SequenceNumber>).getOrElse(())}
          </SendMessageResult>
          <ResponseMetadata>
            <RequestId>{EmptyRequestId}</RequestId>
          </ResponseMetadata>
        </SendMessageResponse>
  }
}
