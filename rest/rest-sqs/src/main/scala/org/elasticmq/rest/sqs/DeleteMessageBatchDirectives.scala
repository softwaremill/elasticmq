package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.DeliveryReceipt
import org.elasticmq.msg.DeleteMessage
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.Action.DeleteMessageBatch
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.xml.Elem

trait DeleteMessageBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule with ResponseMarshaller =>
  def deleteMessageBatch(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(DeleteMessageBatch) {

      val batch = p.as[BatchRequest[DeleteMessageBatchEntry]]

      queueActorFromUrl(batch.QueueUrl) { queueActor =>
        val resultsFuture = batchRequest(batch.Entries) { (messageData, id, _) =>
          val receiptHandle = messageData.ReceiptHandle
          val result = queueActor ? DeleteMessage(DeliveryReceipt(receiptHandle))

          result.flatMap {
            case Right(_) => Future.successful(BatchDeleteMessageResponseEntry(id))
            case Left(invalidHandle) =>
              Future.failed(new SQSException(invalidHandle.code, errorMessage = Some(invalidHandle.message)))
          }
        }
        complete(resultsFuture)
      }
    }
  }
}

case class BatchDeleteMessageResponseEntry(Id: String)

object BatchDeleteMessageResponseEntry {
  implicit val jsonFormat: RootJsonFormat[BatchDeleteMessageResponseEntry] = jsonFormat1(
    BatchDeleteMessageResponseEntry.apply
  )

  implicit val entryXmlSerializer: XmlSerializer[BatchDeleteMessageResponseEntry] =
    new XmlSerializer[BatchDeleteMessageResponseEntry] {
      override def toXml(t: BatchDeleteMessageResponseEntry): Elem =
        <DeleteMessageBatchResultEntry>
          <Id>{t.Id}</Id>
        </DeleteMessageBatchResultEntry>
    }

  implicit def batchXmlSerializer[T](implicit successSerializer: XmlSerializer[T]): XmlSerializer[BatchResponse[T]] =
    new XmlSerializer[BatchResponse[T]] {
      override def toXml(t: BatchResponse[T]): Elem =
        <DeleteMessageBatchResponse>
        <DeleteMessageBatchResult>
          {t.Successful.map(successSerializer.toXml) ++ t.Failed.toList.flatMap(_.map(XmlSerializer[Failed].toXml))}
        </DeleteMessageBatchResult>
        <ResponseMetadata>
          <RequestId>{EmptyRequestId}</RequestId>
        </ResponseMetadata>
      </DeleteMessageBatchResponse>
    }
}
case class DeleteMessageBatchEntry(Id: String, ReceiptHandle: String) extends BatchEntry

object DeleteMessageBatchEntry {
  implicit val jsonFormat: RootJsonFormat[DeleteMessageBatchEntry] = jsonFormat2(DeleteMessageBatchEntry.apply)

  implicit val queryReader: BatchFlatParamsReader[DeleteMessageBatchEntry] =
    new BatchFlatParamsReader[DeleteMessageBatchEntry] {
      override def batchPrefix: String = "DeleteMessageBatchRequestEntry"

      override def read(params: Map[String, String]): DeleteMessageBatchEntry =
        DeleteMessageBatchEntry(
          requiredParameter(params)(IdSubParameter),
          requiredParameter(params)(ReceiptHandleParameter)
        )
    }
}
