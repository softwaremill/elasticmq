package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.{DeliveryReceipt, MillisVisibilityTimeout}
import org.elasticmq.msg.UpdateVisibilityTimeout
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.Action.ChangeMessageVisibilityBatch
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.Future

trait ChangeMessageVisibilityBatchDirectives {
  this: ElasticMQDirectives with BatchRequestsModule =>
  def changeMessageVisibilityBatch(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(ChangeMessageVisibilityBatch) {
      val batch = p.as[BatchRequest[ChangeMessageVisibilityBatchEntry]]

      queueActorFromUrl(batch.QueueUrl) { queueActor =>
        val resultsFuture =
          batchRequest(batch.Entries) { (messageData, id, _) =>
            val result = queueActor ? UpdateVisibilityTimeout(
              DeliveryReceipt(messageData.ReceiptHandle),
              MillisVisibilityTimeout.fromSeconds(messageData.VisibilityTimeout)
            )

            result.flatMap{
              case Right(_) => Future.successful(SuccessfulBatchChangeMessageVisibility(id))
              case Left(invalidHandle) =>
                Future.failed(new SQSException(invalidHandle.code, errorMessage = Some(invalidHandle.message)))
            }
          }

        protocol match {
          case AWSProtocol.`AWSJsonProtocol1.0` =>
            complete(resultsFuture)
          case _ =>
          resultsFuture.map {
            case BatchResponse(failed, succeeded) =>

              val successEntries = succeeded.map {
                case SuccessfulBatchChangeMessageVisibility(id) =>
                  <ChangeMessageVisibilityBatchResultEntry>
                    <Id>
                      {id}
                    </Id>
                  </ChangeMessageVisibilityBatchResultEntry>
              }

              val failureEntries = failed.map {
                case Failed(code, id, message, _) =>
                  <BatchResultErrorEntry>
                    <Id>
                      {id}
                    </Id>
                    <SenderFault>true</SenderFault>
                    <Code>
                      {code}
                    </Code>
                    <Message>
                      {message}
                    </Message>
                  </BatchResultErrorEntry>
              }

              respondWith {
                <ChangeMessageVisibilityBatchResponse>
                  <ChangeMessageVisibilityBatchResult>
                    {successEntries ++ failureEntries}
                  </ChangeMessageVisibilityBatchResult>
                  <ResponseMetadata>
                    <RequestId>
                      {EmptyRequestId}
                    </RequestId>
                  </ResponseMetadata>
                </ChangeMessageVisibilityBatchResponse>
              }
          }
        }
      }
    }
  }
}

case class SuccessfulBatchChangeMessageVisibility(Id: String)

object SuccessfulBatchChangeMessageVisibility {
  implicit val jsonFormat: RootJsonFormat[SuccessfulBatchChangeMessageVisibility] = jsonFormat1(SuccessfulBatchChangeMessageVisibility.apply)
}

case class ChangeMessageVisibilityBatchEntry(Id: String, ReceiptHandle: String, VisibilityTimeout: Long) extends BatchEntry

object ChangeMessageVisibilityBatchEntry {
  implicit val jsonFormat: RootJsonFormat[ChangeMessageVisibilityBatchEntry] = jsonFormat3(ChangeMessageVisibilityBatchEntry.apply)

  implicit val queryReader: BatchFlatParamsReader[ChangeMessageVisibilityBatchEntry] = new BatchFlatParamsReader[ChangeMessageVisibilityBatchEntry] {
    override def read(params: Map[String, String]): ChangeMessageVisibilityBatchEntry =
      ChangeMessageVisibilityBatchEntry(
        requiredParameter(params)(IdSubParameter),
        requiredParameter(params)(ReceiptHandleParameter),
        requiredParameter(params)(VisibilityTimeoutParameter).toLong
      )

    override def batchPrefix: String = "ChangeMessageVisibilityBatchRequestEntry"
  }
}
