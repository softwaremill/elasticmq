package org.elasticmq.rest.sqs

import org.elasticmq.actor.reply._
import org.elasticmq.msg.ListDeadLetterSourceQueues
import org.elasticmq.rest.sqs.Action.{ListDeadLetterSourceQueues => ListDeadLetterSourceQueuesAction}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.xml.Elem

trait ListDeadLetterSourceQueuesDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>
  def listDeadLetterSourceQueues(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(ListDeadLetterSourceQueuesAction) {
      val payload = p.as[ListDeadLetterSourceQueuesActionRequest]
      (getQueueNameFromQueueUrl(payload.QueueUrl) & baseQueueURL)
        .tmap { case (deadLetterQueueName, baseURL) =>
          for {
            queueNames <- queueManagerActor ? ListDeadLetterSourceQueues(deadLetterQueueName)
            queueUrls = queueNames.map(queueName => s"$baseURL/$queueName")
          } yield {
            complete(ListDeadLetterSourceQueuesResponse(queueUrls))
          }
        }
        .tapply(f => f._1)
    }
  }
}

case class ListDeadLetterSourceQueuesActionRequest(
    MaxResults: Option[Int],
    NextToken: Option[String],
    QueueUrl: String
)
object ListDeadLetterSourceQueuesActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[ListDeadLetterSourceQueuesActionRequest] = jsonFormat3(ListDeadLetterSourceQueuesActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[ListDeadLetterSourceQueuesActionRequest] =
    new FlatParamsReader[ListDeadLetterSourceQueuesActionRequest] {
      override def read(params: Map[String, String]): ListDeadLetterSourceQueuesActionRequest = {
        new ListDeadLetterSourceQueuesActionRequest(
          params.get(MaxResultsParameter).map(_.toInt),
          params.get(NextTokenParameter),
          requiredParameter(params)(QueueUrlParameter)
        )
      }
    }
}

case class ListDeadLetterSourceQueuesResponse(queueUrls: List[String])
object ListDeadLetterSourceQueuesResponse {
  implicit val format: RootJsonFormat[ListDeadLetterSourceQueuesResponse] = jsonFormat1(ListDeadLetterSourceQueuesResponse.apply)

  implicit val xmlSerializer: XmlSerializer[ListDeadLetterSourceQueuesResponse] = new XmlSerializer[ListDeadLetterSourceQueuesResponse] {
    override def toXml(t: ListDeadLetterSourceQueuesResponse): Elem = {

      <ListDeadLetterSourceQueuesResponse>
        <ListDeadLetterSourceQueuesResult>
          {t.queueUrls.map(queueUrl => <QueueUrl>{queueUrl}</QueueUrl>)}
        </ListDeadLetterSourceQueuesResult>
        <ResponseMetadata>
          <RequestId>{EmptyRequestId}</RequestId>
        </ResponseMetadata>
      </ListDeadLetterSourceQueuesResponse>
    }
  }
}
