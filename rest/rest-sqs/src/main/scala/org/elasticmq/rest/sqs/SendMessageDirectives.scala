package org.elasticmq.rest.sqs

import java.security.MessageDigest

import Constants._
import MD5Util._
import ParametersUtil._
import org.elasticmq._
import annotation.tailrec

import akka.actor.ActorRef
import scala.concurrent.Future

import akka.http.scaladsl.server.Route
import org.elasticmq.msg.SendMessage
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait SendMessageDirectives { this: ElasticMQDirectives with SQSLimitsModule =>
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"
  val MessageGroupIdParameter = "MessageGroupId"
  val MessageDeduplicationIdParameter = "MessageDeduplicationId"

  def sendMessage(p: AnyParams): Route = {
    p.action("SendMessage") {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        doSendMessage(queueActor, p, queueData).map {
          case (message, digest, messageAttributeDigest) =>
            respondWith {
              <SendMessageResponse>
              <SendMessageResult>
                <MD5OfMessageAttributes>{messageAttributeDigest}</MD5OfMessageAttributes>
                <MD5OfMessageBody>{digest}</MD5OfMessageBody>
                <MessageId>{message.id.id}</MessageId>
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
      .map {
        case (k, _) =>
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

      val value = primaryDataType match {
        case "String" =>
          StringMessageAttribute(parameters("MessageAttribute." + i + ".Value.StringValue"), customDataType)
        case "Number" =>
          val strValue =
            parameters("MessageAttribute." + i + ".Value.StringValue")
          verifyMessageNumberAttribute(strValue)
          NumberMessageAttribute(strValue, customDataType)
        case "Binary" =>
          BinaryMessageAttribute.fromBase64(parameters("MessageAttribute." + i + ".Value.BinaryValue"), customDataType)
        case _ =>
          throw new Exception("Currently only handles String, Number and Binary typed attributes")
      }

      (name, value)
    }.toMap
  }

  def doSendMessage(queueActor: ActorRef,
                    parameters: Map[String, String],
                    queueData: QueueData): Future[(MessageData, String, String)] = {
    val body = parameters(MessageBodyParameter)
    val messageAttributes = getMessageAttributes(parameters)

    ifStrictLimits(bodyContainsInvalidCharacters(body)) {
      "InvalidMessageContents"
    }

    verifyMessageNotTooLong(body.length)

    val messageGroupId = parameters.get(MessageGroupIdParameter) match {
      // MessageGroupId is only supported for FIFO queues
      case Some(_) if !queueData.isFifo => throw SQSException.invalidParameterValue

      // MessageGroupId is required for FIFO queues
      case None if queueData.isFifo => throw SQSException.invalidParameterValue

      // Ensure the given value is valid
      case Some(id) if !isValidFifoPropertyValue(id) => throw SQSException.invalidParameterValue

      // This must be a correct value (or this isn't a FIFO queue and no value is required)
      case m => m
    }

    val messageDeduplicationId = parameters.get(MessageDeduplicationIdParameter) match {
      // MessageDeduplicationId is only supported for FIFO queues
      case Some(_) if !queueData.isFifo => throw SQSException.invalidParameterValue

      // Ensure the given value is valid
      case Some(id) if !isValidFifoPropertyValue(id) => throw SQSException.invalidParameterValue

      // If a valid message group id is provided, use it, as it takes priority over the queue's content based deduping
      case Some(id) => Some(id)

      // MessageDeduplicationId is required for FIFO queues that don't have content based deduplication
      case None if queueData.isFifo && !queueData.hasContentBasedDeduplication =>
        throw SQSException.invalidParameterValue

      // If no MessageDeduplicationId was provided and content based deduping is enabled for queue, generate one
      case None if queueData.isFifo && queueData.hasContentBasedDeduplication => Some(sha256Hash(body))

      // This must be a non-FIFO queue that doesn't require a dedup id
      case None => None
    }

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter) match {
      // FIFO queues don't support delays
      case Some(_) if queueData.isFifo => throw SQSException.invalidParameterValue
      case d                           => d
    }
    val messageToSend =
      createMessage(body, messageAttributes, delaySecondsOption, messageGroupId, messageDeduplicationId)
    val digest = md5Digest(body)
    val messageAttributeDigest = md5AttributeDigest(messageAttributes)

    for {
      message <- queueActor ? SendMessage(messageToSend)
    } yield (message, digest, messageAttributeDigest)
  }

  def verifyMessageNotTooLong(messageLength: Int): Unit = {
    ifStrictLimits(messageLength > 262144) {
      "MessageTooLong"
    }
  }

  private def bodyContainsInvalidCharacters(body: String) = {
    val bodyLength = body.length

    @tailrec
    def findInvalidCharacter(offset: Int): Boolean = {
      if (offset < bodyLength) {
        val c = body.codePointAt(offset)

        // Allow chars: #x9 | #xA | #xD | [#x20 to #xD7FF] | [#xE000 to #xFFFD] | [#x10000 to #x10FFFF]
        if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD) || (c >= 0x10000 && c <= 0x10FFFF)) {
          // Current char is valid
          findInvalidCharacter(offset + Character.charCount(c))
        } else {
          true
        }
      } else {
        false
      }
    }

    findInvalidCharacter(0)
  }

  private def createMessage(body: String,
                            messageAttributes: Map[String, MessageAttribute],
                            delaySecondsOption: Option[Long],
                            groupId: Option[String],
                            deduplicationId: Option[String]) = {
    val nextDelivery = delaySecondsOption match {
      case None               => ImmediateNextDelivery
      case Some(delaySeconds) => AfterMillisNextDelivery(delaySeconds * 1000)
    }

    NewMessageData(None, body, messageAttributes, nextDelivery, groupId, deduplicationId)
  }

  private def sha256Hash(text: String): String = {
    String.format("%064x",
                  new java.math.BigInteger(1, MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))))
  }
}
