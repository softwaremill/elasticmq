package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ListQueues
import org.elasticmq.rest.sqs.Action.{ListQueues => ListQueuesAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import org.elasticmq.rest.sqs.model.RequestPayload

import scala.xml.Elem

trait ListQueuesDirectives { this: ElasticMQDirectives with QueueURLModule with AkkaSupport =>
  def listQueues(p: RequestPayload)(implicit protocol: AWSProtocol) = {
    p.action(ListQueuesAction) {
      rootPath {
        val payload = p.as[ListQueuesActionRequest]

        val prefixOption = payload.QueueNamePrefix
        for {
          allQueueNames <- queueManagerActor ? ListQueues()
        } yield {
          val queueNames = prefixOption match {
            case Some(prefix) => allQueueNames.filter(_.startsWith(prefix))
            case None         => allQueueNames
          }

          baseQueueURL { baseURL =>
            complete(ListQueuesResponse(queueNames.map(queueName => s"$baseURL/$queueName").toList))
          }
        }
      }
    }
  }
}

case class ListQueuesActionRequest(
    MaxResults: Option[Int],
    NextToken: Option[String],
    QueueNamePrefix: Option[String]
)

object ListQueuesActionRequest {

  implicit val requestJsonFormat: RootJsonFormat[ListQueuesActionRequest] = jsonFormat3(ListQueuesActionRequest.apply)

  implicit val requestParamReader: FlatParamsReader[ListQueuesActionRequest] =
    new FlatParamsReader[ListQueuesActionRequest] {
      override def read(params: Map[String, String]): ListQueuesActionRequest = {
        new ListQueuesActionRequest(
          params.get(MaxResultsParameter).map(_.toInt),
          params.get(NextTokenParameter),
          params.get(QueueNamePrefixParameter)
        )
      }
    }

}

case class ListQueuesResponse(QueueUrls: List[String])

object ListQueuesResponse {
  implicit val format: RootJsonFormat[ListQueuesResponse] = jsonFormat1(ListQueuesResponse.apply)

  implicit val xmlSerializer: XmlSerializer[ListQueuesResponse] = new XmlSerializer[ListQueuesResponse] {
    override def toXml(t: ListQueuesResponse): Elem = {

      <ListQueuesResponse>
        <ListQueuesResult>
          {t.QueueUrls.map(queueUrl => <QueueUrl>{queueUrl}</QueueUrl>)}
        </ListQueuesResult>
        <ResponseMetadata>
          <RequestId>{EmptyRequestId}</RequestId>
        </ResponseMetadata>
      </ListQueuesResponse>
    }
  }
}
