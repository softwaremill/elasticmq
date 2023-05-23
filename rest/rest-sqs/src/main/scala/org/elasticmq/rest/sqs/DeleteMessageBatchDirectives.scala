package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.Action.DeleteMessageBatch
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

import scala.xml.{Node, Text}

trait DeleteMessageBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule =>
  def deleteMessageBatch(p: AnyParams) = {
    p.action(DeleteMessageBatch) {
      ???
//      queueActorFromRequest(p) { queueActor =>
//        val resultsFuture = batchRequest("DeleteMessageBatchRequestEntry", p) { (messageData, id, _) =>
//          val receiptHandle = messageData(ReceiptHandleParameter)
//          val result = queueActor ? DeleteMessage(DeliveryReceipt(receiptHandle))
//
//          result.map {
//            case Left(error) =>
//              <BatchResultErrorEntry>
//                <Id>{id}</Id>
//                <Code>{error.code}</Code>
//                <Message>{error.message}</Message>
//              </BatchResultErrorEntry>
//            case Right(_) =>
//              <DeleteMessageBatchResultEntry>
//                <Id>{id}</Id>
//              </DeleteMessageBatchResultEntry>
//          }
//        }
//
//        resultsFuture.map { results =>
//          respondWith {
//            <DeleteMessageBatchResponse>
//              <DeleteMessageBatchResult>
//                {results}
//              </DeleteMessageBatchResult>
//              <ResponseMetadata>
//                <RequestId>{EmptyRequestId}</RequestId>
//              </ResponseMetadata>
//            </DeleteMessageBatchResponse>
//          }
//        }
//      }
    }
  }
}
