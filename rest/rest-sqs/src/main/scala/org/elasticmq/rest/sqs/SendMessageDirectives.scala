package org.elasticmq.rest.sqs

import Constants._
import MD5Util._
import ParametersUtil._
import org.elasticmq.{ImmediateNextDelivery, AfterMillisNextDelivery}
import annotation.tailrec
import akka.actor.ActorRef
import org.elasticmq.data.{MessageData, NewMessageData}
import scala.concurrent.Future
import org.elasticmq.msg.SendMessage
import org.elasticmq.actor.reply._

trait SendMessageDirectives { this: ElasticMQDirectives with SQSLimitsModule =>
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"

  val sendMessage = {
    action("SendMessage") {
      queueActorFromPath { queueActor =>
        anyParamsMap { parameters =>
          doSendMessage(queueActor, parameters).map { case (message, digest) =>
            respondWith {
              <SendMessageResponse>
                <SendMessageResult>
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
  }

  def doSendMessage(queueActor: ActorRef, parameters: Map[String, String]): Future[(MessageData, String)] = {
    val body = parameters(MessageBodyParameter)

    ifStrictLimits(bodyContainsInvalidCharacters(body)) {
      "InvalidMessageContents"
    }

    verifyMessageNotTooLong(body.length)

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter)
    val messageToSend = createMessage(body, delaySecondsOption)
    val digest = md5Digest(body)

    for {
      message <- queueActor ? SendMessage(messageToSend)
    } yield (message, digest)
  }

  def verifyMessageNotTooLong(messageLength: Int) {
    ifStrictLimits(messageLength > 65536) {
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

  private def createMessage(body: String, delaySecondsOption: Option[Long]) = {
    val nextDelivery = delaySecondsOption match {
      case None => ImmediateNextDelivery
      case Some(delaySeconds) => AfterMillisNextDelivery(delaySeconds*1000)
    }

    NewMessageData(None, body, nextDelivery)
  }
}