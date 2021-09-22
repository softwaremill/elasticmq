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
import org.elasticmq.rest.sqs.model.{
  BinaryMessageSystemAttribute,
  MessageSystemAttribute,
  NumberMessageSystemAttribute,
  StringMessageSystemAttribute
}

import scala.concurrent.Future

trait SendMessageDirectives { this: ElasticMQDirectives with SQSLimitsModule =>
  private val messageSystemAttributeNamePattern = """MessageSystemAttribute\.(\d+)\.Name""".r
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"
  val MessageGroupIdParameter = "MessageGroupId"
  val MessageDeduplicationIdParameter = "MessageDeduplicationId"
  val AwsTraceHeaderSystemAttribute = "AWSTraceHeader"
  val AwsTraceIdHeaderName = "X-Amzn-Trace-Id"

  def sendMessage(p: AnyParams): Route = {
    p.action(SendMessageAction) {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        val message = createMessage(p, queueData, orderIndex = 0)

        doSendMessage(queueActor, message).map { case (message, digest, messageAttributeDigest) =>
          respondWith {
            <SendMessageResponse>
                <SendMessageResult>
                  {messageAttributeDigest.map(d => <MD5OfMessageAttributes>{d}</MD5OfMessageAttributes>).getOrElse(())}
                  <MD5OfMessageBody>{digest}</MD5OfMessageBody>
                  <MessageId>{message.id.id}</MessageId>
                  {message.messageAttributes.get("SequenceNumber").flatMap(sn => {
                    sn match {
                      case StringMessageAttribute(x, _) => Some(<SequenceNumber>{x}</SequenceNumber>)
                      case _ => None
                    }
                }).getOrElse(())}
                </SendMessageResult>
                <ResponseMetadata>
                  <RequestId>{EmptyRequestId}</RequestId>
                </ResponseMetadata>
              </SendMessageResponse>
          }
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

    Limits.verifyMessageAttributesNumber(numAttributes, sqsLimits)
      .fold(error => throw new SQSException(error), identity)

    (1 to numAttributes).map { i =>
      val name = parameters("MessageAttribute." + i + ".Name")
      val dataType = parameters("MessageAttribute." + i + ".Value.DataType")

      val primaryDataType = dataType.split('.')(0)
      val customDataType = if (dataType.contains('.')) {
        Some(dataType.substring(dataType.indexOf('.') + 1))
      } else {
        None
      }

      val value = primaryDataType match {
        case "String" =>
          val strValue =
            parameters("MessageAttribute." + i + ".Value.StringValue")
          Limits
            .verifyMessageStringAttribute(name, strValue, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
          StringMessageAttribute(strValue, customDataType)
        case "Number" =>
          val strValue =
            parameters("MessageAttribute." + i + ".Value.StringValue")
          Limits
            .verifyMessageNumberAttribute(strValue, name, sqsLimits)
            .fold(error => throw new SQSException(error), identity)
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

  private def getMessageSystemAttributes(parameters: Map[String, String]): Map[String, MessageSystemAttribute] = {
    parameters.flatMap {
      case (messageSystemAttributeNamePattern(index), parameterName) =>
        val parameterDataType = parameters(s"MessageSystemAttribute.$index.Value.DataType")
        val parameterValue = parameterDataType match {
          case "String" => StringMessageSystemAttribute(parameters(s"MessageSystemAttribute.$index.Value.StringValue"))
          case "Number" => NumberMessageSystemAttribute(parameters(s"MessageSystemAttribute.$index.Value.StringValue"))
          case "Binary" =>
            BinaryMessageSystemAttribute.fromString(parameters(s"MessageAttribute.$index.Value.BinaryValue"))
        }
        Some((parameterName, parameterValue))
      case _ => None
    }
  }

  def createMessage(parameters: Map[String, String], queueData: QueueData, orderIndex: Int): NewMessageData = {
    val body = parameters(MessageBodyParameter)
    val messageAttributes = getMessageAttributes(parameters)
    val messageSystemAttributes = getMessageSystemAttributes(parameters)

    Limits.verifyMessageBody(body, sqsLimits).fold(error => throw new SQSException(error), identity)

    val messageGroupId = parameters.get(MessageGroupIdParameter) match {
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

    val messageDeduplicationId = parameters.get(MessageDeduplicationIdParameter) match {
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

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter) match {
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
        case StringMessageSystemAttribute(value) => TracingId(value)
        case NumberMessageSystemAttribute(_) =>
          throw new SQSException(
            InvalidParameterValueErrorName,
            errorMessage = Some(
              s"$AwsTraceHeaderSystemAttribute should be declared as a String, instead it was recognized as a Number"
            )
          )
        case BinaryMessageSystemAttribute(_) =>
          throw new SQSException(
            InvalidParameterValueErrorName,
            errorMessage = Some(
              s"$AwsTraceHeaderSystemAttribute should be declared as a String, instead it was recognized as a Binary value"
            )
          )
      }
      .orElse(parameters.get(AwsTraceIdHeaderName).map(TracingId.apply))

    NewMessageData(
      None,
      body,
      messageAttributes,
      nextDelivery,
      messageGroupId,
      messageDeduplicationId,
      orderIndex,
      maybeTracingId
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
}
