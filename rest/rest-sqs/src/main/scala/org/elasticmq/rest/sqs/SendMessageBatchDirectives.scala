package org.elasticmq.rest.sqs

import Constants._
import akka.http.scaladsl.server.Route
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait SendMessageBatchDirectives {
  this: ElasticMQDirectives with SendMessageDirectives with BatchRequestsModule =>
  val SendMessageBatchPrefix = "SendMessageBatchRequestEntry"

  def sendMessageBatch(p: AnyParams): Route = {
    p.action("SendMessageBatch") {
      queueActorAndDataFromRequest(p) { (queueActor, queueData) =>
        verifyMessagesNotTooLong(p)
        val resultsFuture = batchRequest(SendMessageBatchPrefix, p) { (messageData, id) =>
          doSendMessage(queueActor, messageData, queueData).map {
            case (message, digest, messageAttributeDigest) =>
              <SendMessageBatchResultEntry>
              <Id>{id}</Id>
              <MD5OfMessageAttributes>{messageAttributeDigest}</MD5OfMessageAttributes>
              <MD5OfMessageBody>{digest}</MD5OfMessageBody>
              <MessageId>{message.id.id}</MessageId>
            </SendMessageBatchResultEntry>
          }
        }

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

  def verifyMessagesNotTooLong(parameters: Map[String, String]) {
    val messageLengths = for {
      parameterMap <- batchParametersMap(SendMessageBatchPrefix, parameters)
    } yield {
      parameterMap(MessageBodyParameter).length
    }

    verifyMessageNotTooLong(messageLengths.sum)
  }
}
