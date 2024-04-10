package org.elasticmq.rest.sqs

import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}
import org.elasticmq.actor.reply._
import org.elasticmq.msg.UpdateVisibilityTimeout
import org.elasticmq.rest.sqs.Action.ChangeMessageVisibilityBatch
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.Future
import scala.xml.Elem

trait ChangeMessageVisibilityBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule with ResponseMarshaller =>
  def changeMessageVisibilityBatch(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(ChangeMessageVisibilityBatch) {
      val batch = p.as[BatchRequest[ChangeMessageVisibilityBatchEntry]]

      queueActorFromUrl(batch.QueueUrl) { queueActor =>
        val resultsFuture =
          batchRequest(batch.Entries) { (messageData, id, _) =>
            val result = queueActor ? UpdateVisibilityTimeout(
              DeliveryReceipt(messageData.ReceiptHandle),
              MillisVisibilityTimeout.fromSeconds(messageData.VisibilityTimeout)
            )

            result.flatMap {
              case Right(_) => Future.successful(BatchChangeMessageVisibilityResponseEntry(id))
              case Left(invalidHandle) =>
                Future.failed(new SQSException(invalidHandle.code, errorMessage = Some(invalidHandle.message)))
            }
          }

        resultsFuture.map(complete(_))
      }
    }
  }
}

case class BatchChangeMessageVisibilityResponseEntry(Id: String)

object BatchChangeMessageVisibilityResponseEntry {
  implicit val jsonFormat: RootJsonFormat[BatchChangeMessageVisibilityResponseEntry] = jsonFormat1(
    BatchChangeMessageVisibilityResponseEntry.apply
  )

  implicit val xmlSerializer: XmlSerializer[BatchChangeMessageVisibilityResponseEntry] =
    new XmlSerializer[BatchChangeMessageVisibilityResponseEntry] {
      override def toXml(t: BatchChangeMessageVisibilityResponseEntry): Elem =
        <ChangeMessageVisibilityBatchResultEntry>
        <Id>{t.Id}</Id>
      </ChangeMessageVisibilityBatchResultEntry>
    }

  implicit def batchXmlSerializer(implicit
      successSerializer: XmlSerializer[BatchChangeMessageVisibilityResponseEntry]
  ): XmlSerializer[BatchResponse[BatchChangeMessageVisibilityResponseEntry]] =
    new XmlSerializer[BatchResponse[BatchChangeMessageVisibilityResponseEntry]] {
      override def toXml(t: BatchResponse[BatchChangeMessageVisibilityResponseEntry]): Elem =
        <ChangeMessageVisibilityBatchResponse>
          <ChangeMessageVisibilityBatchResult>
            {t.Successful.map(successSerializer.toXml) ++ t.Failed.toList.flatMap(_.map(XmlSerializer[Failed].toXml))}
          </ChangeMessageVisibilityBatchResult>
          <ResponseMetadata>
            <RequestId>
              {EmptyRequestId}
            </RequestId>
          </ResponseMetadata>
        </ChangeMessageVisibilityBatchResponse>
    }
}

case class ChangeMessageVisibilityBatchEntry(Id: String, ReceiptHandle: String, VisibilityTimeout: Long)
    extends BatchEntry

object ChangeMessageVisibilityBatchEntry {
  implicit val jsonFormat: RootJsonFormat[ChangeMessageVisibilityBatchEntry] = jsonFormat3(
    ChangeMessageVisibilityBatchEntry.apply
  )

  implicit val queryReader: BatchFlatParamsReader[ChangeMessageVisibilityBatchEntry] =
    new BatchFlatParamsReader[ChangeMessageVisibilityBatchEntry] {
      override def read(params: Map[String, String]): ChangeMessageVisibilityBatchEntry =
        ChangeMessageVisibilityBatchEntry(
          requiredParameter(params)(IdSubParameter),
          requiredParameter(params)(ReceiptHandleParameter),
          requiredParameter(params)(VisibilityTimeoutParameter).toLong
        )

      override def batchPrefix: String = "ChangeMessageVisibilityBatchRequestEntry"
    }
}
