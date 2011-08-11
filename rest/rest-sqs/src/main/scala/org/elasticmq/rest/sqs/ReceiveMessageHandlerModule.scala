package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import org.elasticmq.rest.sqs.ActionUtil._
import org.elasticmq.rest.sqs.MD5Util._

trait ReceiveMessageHandlerModule { this: ClientModule with RequestHandlerLogicModule =>
  import ReceiveMessageHandlerModule._

  val receiveMessageLogic = logicWithQueue((queue, request, parameters) => {
    val message = client.messageClient.receiveMessage(queue)

    <ReceiveMessageResponse>
      <ReceiveMessageResult>
        {message.map(m =>
        <Message>
          <MessageId>{m.id}</MessageId>
          <ReceiptHandle>{m.id}</ReceiptHandle>
          <MD5OfBody>{md5Digest(m.content)}</MD5OfBody>
          <Body>{m.content}</Body>
        </Message>).toList}
      </ReceiveMessageResult>
      <ResponseMetadata>
        <RequestId>{EMPTY_REQUEST_ID}</RequestId>
      </ResponseMetadata>
    </ReceiveMessageResponse>
  })

  val receiveMessageGetHandler = (createHandler
            forMethod GET
            forPath (QUEUE_PATH)
            requiringParameterValues Map(RECEIVE_MESSAGE_ACTION)
            running receiveMessageLogic)
}

object ReceiveMessageHandlerModule {
  val RECEIVE_MESSAGE_ACTION = createAction("ReceiveMessage")
}