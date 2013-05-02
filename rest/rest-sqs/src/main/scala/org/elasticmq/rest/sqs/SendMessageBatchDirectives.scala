package org.elasticmq.rest.sqs

import Constants._

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
