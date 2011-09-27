package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.Message

import Constants._
import ActionUtil._
import MD5Util._

trait SendMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  import SendMessageHandlerModule._

  val sendMessageLogic = logicWithQueue((queue, request, parameters) => {
    val body = parameters(MESSAGE_BODY_PARAMETER)
    val message = client.messageClient.sendMessage(Message(queue, body))

    val digest = md5Digest(body)

    <SendMessageResponse>
      <SendMessageResult>
        <MD5OfMessageBody>{digest}</MD5OfMessageBody>
        <MessageId>{message.id.get}</MessageId>
      </SendMessageResult>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </SendMessageResponse>
  })

  val sendMessageGetHandler = (createHandler
            forMethod GET
            forPath (QUEUE_PATH)
            requiringParameters List(MESSAGE_BODY_PARAMETER)
            requiringParameterValues Map(SEND_MESSAGE_ACTION)
            running sendMessageLogic)

  val sendMessagePostHandler = (createHandler
            forMethod POST
            forPath (QUEUE_PATH)
            includingParametersFromBody ()
            requiringParameters List(MESSAGE_BODY_PARAMETER)
            requiringParameterValues Map(SEND_MESSAGE_ACTION)
            running sendMessageLogic)
}

object SendMessageHandlerModule {
  val SEND_MESSAGE_ACTION = createAction("SendMessage")
  val MESSAGE_BODY_PARAMETER = "MessageBody"
}