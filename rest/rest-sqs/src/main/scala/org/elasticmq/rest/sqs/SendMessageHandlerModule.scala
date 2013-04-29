package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._
import MD5Util._
import ParametersUtil._
import org.elasticmq.{MessageBuilder, AfterMillisNextDelivery, Queue}
import annotation.tailrec

trait SendMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule with SQSLimitsModule =>
  val sendMessageLogic = logicWithQueue((queue, request, parameters) => {
    val (message, digest) = sendMessage(queue, parameters)

    <SendMessageResponse>
      <SendMessageResult>
        <MD5OfMessageBody>{digest}</MD5OfMessageBody>
        <MessageId>{message.id.id}</MessageId>
      </SendMessageResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SendMessageResponse>
  })

  def sendMessage(queue: Queue, parameters: Map[String, String]) = {
    val body = parameters(MessageBodyParameter)

    ifStrictLimits(bodyContainsInvalidCharacters(body)) {
      "InvalidMessageContents"
    }

    verifyMessageNotTooLong(body.length)

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter)
    val messageToSend = createMessage(queue, body, delaySecondsOption)
    val message = queue.sendMessage(messageToSend)

    val digest = md5Digest(body)

    (message, digest)
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

  private def createMessage(queue: Queue, body: String, delaySecondsOption: Option[Long]) = {
    val base = MessageBuilder(body)
    delaySecondsOption match {
      case None => base
      case Some(delaySeconds) => base.withNextDelivery(AfterMillisNextDelivery(delaySeconds*1000))
    }    
  }  

  val SendMessageAction = createAction("SendMessage")
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"

  val sendMessageGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameters List(MessageBodyParameter)
            requiringParameterValues Map(SendMessageAction)
            running sendMessageLogic)

  val sendMessagePostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameters List(MessageBodyParameter)
            requiringParameterValues Map(SendMessageAction)
            running sendMessageLogic)
}

trait SendMessageDirectives { this: ElasticMQDirectives with SQSLimitsModule =>
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"

  val sendMesage = {
    action("SendMessage") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          val (message, digest) = sendMessage(queue, parameters)

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

  def sendMessage(queue: Queue, parameters: Map[String, String]) = {
    val body = parameters(MessageBodyParameter)

    ifStrictLimits(bodyContainsInvalidCharacters(body)) {
      "InvalidMessageContents"
    }

    verifyMessageNotTooLong(body.length)

    val delaySecondsOption = parameters.parseOptionalLong(DelaySecondsParameter)
    val messageToSend = createMessage(queue, body, delaySecondsOption)
    val message = queue.sendMessage(messageToSend)

    val digest = md5Digest(body)

    (message, digest)
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

  private def createMessage(queue: Queue, body: String, delaySecondsOption: Option[Long]) = {
    val base = MessageBuilder(body)
    delaySecondsOption match {
      case None => base
      case Some(delaySeconds) => base.withNextDelivery(AfterMillisNextDelivery(delaySeconds*1000))
    }
  }
}