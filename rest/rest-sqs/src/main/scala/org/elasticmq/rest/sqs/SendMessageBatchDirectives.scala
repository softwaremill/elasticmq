package org.elasticmq.rest.sqs

import Constants._
import akka.http.scaladsl.server.Route
import org.elasticmq.MessageAttribute
import org.elasticmq.rest.sqs.Action.SendMessageBatch
import org.elasticmq.rest.sqs.ParametersUtil.ParametersParser
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.xml.Elem

trait SendMessageBatchDirectives {
  this: ElasticMQDirectives with SendMessageDirectives with BatchRequestsModule with SQSLimitsModule with ResponseMarshaller =>

  def sendMessageBatch(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(SendMessageBatch) {
      val batch = p.as[BatchRequest[SendMessageBatchActionRequest]]
      queueActorAndDataFromQueueUrl(batch.QueueUrl) { (queueActor, queueData) =>
        verifyMessagesNotTooLong(batch.Entries)

        val resultsFuture = batchRequest(batch.Entries) { (messageData, id, index) =>
          validateMessageAttributes(messageData.MessageAttributes.getOrElse(Map.empty))

          val message =
            createMessage(messageData.toSendMessageActionRequest(batch.QueueUrl), queueData, index, p.xRayTracingHeader)

          doSendMessage(queueActor, message).map { case (message, digest, messageAttributeDigest) =>
            BatchMessageSendResponseEntry(id, messageAttributeDigest, digest, None, message.id.id, None)
          }
        }
        complete(resultsFuture)
      }
    }
  }

  private def verifyMessagesNotTooLong(requests: List[SendMessageBatchActionRequest]): Unit = {
    val messageLengths = requests.map(_.MessageBody.length)
    verifyMessageNotTooLong(messageLengths.sum)
  }

  case class SendMessageBatchActionRequest(
      Id: String,
      DelaySeconds: Option[Long],
      MessageBody: String,
      MessageDeduplicationId: Option[String],
      MessageGroupId: Option[String],
      MessageSystemAttributes: Option[Map[String, MessageAttribute]],
      MessageAttributes: Option[Map[String, MessageAttribute]]
  ) extends BatchEntry {
    def toSendMessageActionRequest(queueUrl: String): SendMessageActionRequest = SendMessageActionRequest(
      DelaySeconds,
      MessageBody,
      MessageDeduplicationId,
      MessageGroupId,
      MessageSystemAttributes,
      MessageAttributes,
      queueUrl
    )
  }

  object SendMessageBatchActionRequest extends MessageAttributesSupport {

    implicit val jsonFormat: RootJsonFormat[SendMessageBatchActionRequest] = jsonFormat7(
      SendMessageBatchActionRequest.apply
    )

    implicit val queryFormat: BatchFlatParamsReader[SendMessageBatchActionRequest] = {
      new BatchFlatParamsReader[SendMessageBatchActionRequest] {
        override def read(params: Map[String, String]): SendMessageBatchActionRequest = {
          SendMessageBatchActionRequest(
            requiredParameter(params)(IdSubParameter),
            params.parseOptionalLong(DelaySecondsParameter),
            requiredParameter(params)(MessageBodyParameter),
            params.get(MessageDeduplicationIdParameter),
            params.get(MessageGroupIdParameter),
            Some(getMessageSystemAttributes(params)),
            Some(getMessageAttributes(params))
          )
        }

        override def batchPrefix: String = "SendMessageBatchRequestEntry"
      }
    }
  }

  case class BatchMessageSendResponseEntry(
      Id: String,
      MD5OfMessageAttributes: Option[String],
      MD5OfMessageBody: String,
      MD5OfMessageSystemAttributes: Option[String],
      MessageId: String,
      SequenceNumber: Option[Long]
  )

  object BatchMessageSendResponseEntry {
    implicit val format: RootJsonFormat[BatchMessageSendResponseEntry] = jsonFormat6(
      BatchMessageSendResponseEntry.apply
    )

    implicit val entryXmlSerializer: XmlSerializer[BatchMessageSendResponseEntry] = {
      new XmlSerializer[BatchMessageSendResponseEntry] {
        override def toXml(t: BatchMessageSendResponseEntry): Elem =
          <SendMessageBatchResultEntry>
            <Id>{t.Id}</Id>
            {t.MD5OfMessageAttributes.map(d => <MD5OfMessageAttributes>{d}</MD5OfMessageAttributes>).getOrElse(())}
            <MD5OfMessageBody>{t.MD5OfMessageBody}</MD5OfMessageBody>
            <MessageId>{t.MessageId}</MessageId>
          </SendMessageBatchResultEntry>
      }
    }

    implicit def batchXmlSerializer(implicit successSerializer: XmlSerializer[BatchMessageSendResponseEntry]): XmlSerializer[BatchResponse[BatchMessageSendResponseEntry]]
    = new XmlSerializer[BatchResponse[BatchMessageSendResponseEntry]] {
      override def toXml(t: BatchResponse[BatchMessageSendResponseEntry]): Elem =
        <SendMessageBatchResponse>
          <SendMessageBatchResult>
            {t.Successful.map(successSerializer.toXml) ++ t.Failed.map(XmlSerializer[Failed].toXml)}
          </SendMessageBatchResult>
          <ResponseMetadata>
            <RequestId>
              {EmptyRequestId}
            </RequestId>
          </ResponseMetadata>
        </SendMessageBatchResponse>
    }
  }
}
