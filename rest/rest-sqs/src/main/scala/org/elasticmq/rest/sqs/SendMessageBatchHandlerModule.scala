package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait SendMessageBatchHandlerModule { this: ClientModule with RequestHandlerLogicModule with SendMessageHandlerModule with BatchRequestsModule with SQSLimitsModule =>
  val sendMessageBatchLogic = logicWithQueue((queue, request, parameters) => {
    var totalLength = 0

    val results = batchRequest("SendMessageBatchRequestEntry", parameters) { (messageData, id) =>
      val (message, digest) = sendMessage(queue, messageData)

      totalLength += messageData(MessageBodyParameter).length

      <SendMessageBatchResultEntry>
        <Id>{id}</Id>
        <MD5OfMessageBody>{digest}</MD5OfMessageBody>
        <MessageId>{message.id.id}</MessageId>
      </SendMessageBatchResultEntry>
    }

    verifyMessageNotTooLong(totalLength)

    <SendMessageBatchResponse>
      <SendMessageBatchResult>
        {results}
      </SendMessageBatchResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </SendMessageBatchResponse>
  })

  val SendMessageBatchAction = createAction("SendMessageBatch")

  val sendMessageBatchGetHandler = (createHandler
            forMethod GET
            forPath (QueuePath)
            requiringParameterValues Map(SendMessageBatchAction)
            running sendMessageBatchLogic)

  val sendMessageBatchPostHandler = (createHandler
            forMethod POST
            forPath (QueuePath)
            includingParametersFromBody ()
            requiringParameterValues Map(SendMessageBatchAction)
            running sendMessageBatchLogic)
}

trait SendMessageBatchDirectives { this: ElasticMQDirectives with SendMessageDirectives with BatchRequestsModule =>
  val sendMessageBatch = {
    action("SendMessageBatch") {
      queuePath { queue =>
        anyParamsMap { parameters =>
          var totalLength = 0

          val results = batchRequest("SendMessageBatchRequestEntry", parameters) { (messageData, id) =>
            val (message, digest) = doSendMessage(queue, messageData)

            totalLength += messageData(MessageBodyParameter).length

            <SendMessageBatchResultEntry>
              <Id>{id}</Id>
              <MD5OfMessageBody>{digest}</MD5OfMessageBody>
              <MessageId>{message.id.id}</MessageId>
            </SendMessageBatchResultEntry>
          }

          verifyMessageNotTooLong(totalLength)

          respondWith {
            <SendMessageBatchResponse>
              <SendMessageBatchResult>
                {results}
              </SendMessageBatchResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </SendMessageBatchResponse>
          }
        }
      }
    }
  }
}
