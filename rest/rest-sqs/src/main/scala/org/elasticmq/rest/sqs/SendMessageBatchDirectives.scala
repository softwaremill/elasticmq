package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait SendMessageBatchDirectives { this: ElasticMQDirectives with SendMessageDirectives with BatchRequestsModule =>
  val sendMessageBatch = {
    action("SendMessageBatch") {
      queueActorFromPath { queueActor =>
        anyParamsMap { parameters =>
          var totalLength = 0

          val resultsFuture = batchRequest("SendMessageBatchRequestEntry", parameters) { (messageData, id) =>

            // TODO !!!
            totalLength += messageData(MessageBodyParameter).length

            doSendMessage(queueActor, messageData).map { case (message, digest) =>
              <SendMessageBatchResultEntry>
                <Id>{id}</Id>
                <MD5OfMessageBody>{digest}</MD5OfMessageBody>
                <MessageId>{message.id.id}</MessageId>
              </SendMessageBatchResultEntry>
            }
          }

          verifyMessageNotTooLong(totalLength)

          resultsFuture.map { results =>
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
}
