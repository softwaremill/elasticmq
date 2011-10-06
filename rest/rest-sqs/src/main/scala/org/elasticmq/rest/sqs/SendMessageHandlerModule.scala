package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import org.elasticmq.Message

import Constants._
import ActionUtil._
import MD5Util._

trait SendMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  val sendMessageLogic = logicWithQueue((queue, request, parameters) => {
    val body = parameters(MessageBodyParameter)
    val message = client.messageClient.sendMessage(Message(queue, body))

    val digest = md5Digest(body)

    <SendMessageResponse>
      <SendMessageResult>
        <MD5OfMessageBody>{digest}</MD5OfMessageBody>
        <MessageId>{message.id.get}</MessageId>
      </SendMessageResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SendMessageResponse>
  })

  val SendMessageAction = createAction("SendMessage")
  val MessageBodyParameter = "MessageBody"

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
