package org.elasticmq.rest.sqs

import Constants._
import MD5Util._
import ParametersUtil._
import org.elasticmq._
import annotation.tailrec
import akka.actor.ActorRef
import scala.concurrent.Future
import org.elasticmq.msg.SendMessage
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait SendMessageDirectives { this: ElasticMQDirectives with SQSLimitsModule =>
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"

  def sendMessage(p: AnyParams) = {
    p.action("SendMessage") {
      queueActorFromRequest(p) { queueActor =>
        doSendMessage(queueActor, p).map {
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
        case (k, v) => {
          if (k.startsWith("MessageAttribute.")) {
            k.split("\\.")(1).toInt
          } else {
            0
          }
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
        case "String" => {
          StringMessageAttribute(parameters("MessageAttribute." + i + ".Value.StringValue"), customDataType)
        }
        case "Number" => {
          val strValue =
            parameters("MessageAttribute." + i + ".Value.StringValue")
          verifyMessageNumberAttribute(strValue)
          NumberMessageAttribute(strValue, customDataType)
        }
        case "Binary" => {
          BinaryMessageAttribute.fromBase64(parameters("MessageAttribute." + i + ".Value.BinaryValue"), customDataType)
        }
        case _ => {
          throw new Exception("Currently only handles String, Number and Binary typed attributes")
        }
      }

      (name, value)
    }.toMap
  }

  def doSendMessage(queueActor: ActorRef, parameters: Map[String, String]): Future[(MessageData, String, String)] = {
    val body = parameters(MessageBodyParameter)
    val messageAttributes = getMessageAttributes(parameters)

    ifStrictLimits(bodyContainsInvalidCharacters(body)) {
      "InvalidMessageContents"
    }

    verifyMessageNotTooLong(body.length)

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter)
    val messageToSend =
      createMessage(body, messageAttributes, delaySecondsOption)
    val digest = md5Digest(body)
    val messageAttributeDigest = md5AttributeDigest(messageAttributes)

    for {
      message <- queueActor ? SendMessage(messageToSend)
    } yield (message, digest, messageAttributeDigest)
  }

  def verifyMessageNotTooLong(messageLength: Int) {
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
                            delaySecondsOption: Option[Long]) = {
    val nextDelivery = delaySecondsOption match {
      case None               => ImmediateNextDelivery
      case Some(delaySeconds) => AfterMillisNextDelivery(delaySeconds * 1000)
    }

    NewMessageData(None, body, messageAttributes, nextDelivery)
  }
}
