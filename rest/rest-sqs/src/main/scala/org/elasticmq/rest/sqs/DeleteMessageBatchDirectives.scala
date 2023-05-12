package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.Action.DeleteMessageBatch
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future

trait DeleteMessageBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule =>
  def deleteMessageBatch(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(DeleteMessageBatch) {

      val batch = p.as[BatchRequest[DeleteMessageBatchEntry]]

      queueActorFromUrl(batch.QueueUrl) { queueActor =>
        val resultsFuture = batchRequest(batch.Entries) { (messageData, id, _) =>
          val receiptHandle = messageData.ReceiptHandle
          val result = queueActor ? DeleteMessage(DeliveryReceipt(receiptHandle))

          result.flatMap {
            case Right(_) => Future.successful(BatchDeleteMessageResultEntry(id))
            case Left(invalidHandle) =>
              Future.failed(new SQSException(invalidHandle.code, errorMessage = Some(invalidHandle.message)))
          }
        }

        protocol match {
          case AWSProtocol.`AWSJsonProtocol1.0` =>
            complete(resultsFuture)
          case _ =>
            resultsFuture.map {
              case BatchResponse(failed, successful) =>

                val failureEntries = failed.map {
                  case Failed(code, id, message, _) =>
                    <BatchResultErrorEntry>
                      <Id>{id}</Id>
                      <Code>{code}</Code>
                      <Message>{message}</Message>
                    </BatchResultErrorEntry>
                }

                val successEntries = successful.map {
                  case BatchDeleteMessageResultEntry(id) =>
                    <DeleteMessageBatchResultEntry>
                      <Id>{id}</Id>
                    </DeleteMessageBatchResultEntry>
                }

                respondWith {
                  <DeleteMessageBatchResponse>
                    <DeleteMessageBatchResult>
                      {failureEntries ++ successEntries}
                    </DeleteMessageBatchResult>
                    <ResponseMetadata>
                      <RequestId>{EmptyRequestId}</RequestId>
                    </ResponseMetadata>
                  </DeleteMessageBatchResponse>
                }
            }
        }
      }
    }
  }
}

case class BatchDeleteMessageResultEntry(Id: String)

object BatchDeleteMessageResultEntry {
  implicit val jsonFormat: RootJsonFormat[BatchDeleteMessageResultEntry] = jsonFormat1(BatchDeleteMessageResultEntry.apply)
}
case class DeleteMessageBatchEntry(Id: String, ReceiptHandle: String) extends BatchEntry

object DeleteMessageBatchEntry {
  implicit val jsonFormat: RootJsonFormat[DeleteMessageBatchEntry] = jsonFormat2(DeleteMessageBatchEntry.apply)

  implicit val queryReader: BatchFlatParamsReader[DeleteMessageBatchEntry] = new BatchFlatParamsReader[DeleteMessageBatchEntry] {
    override def batchPrefix: String = "DeleteMessageBatchRequestEntry"

    override def read(params: Map[String, String]): DeleteMessageBatchEntry =
      DeleteMessageBatchEntry(
        requiredParameter(params)(IdSubParameter),
        requiredParameter(params)(ReceiptHandleParameter)
      )
  }
}
