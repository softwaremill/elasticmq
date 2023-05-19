package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ListQueues
import org.elasticmq.rest.sqs.Action.{ListQueues => ListQueuesAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.elasticmq.rest.sqs.model.RequestPayload

trait ListQueuesDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def listQueues(p: RequestPayload, protocol: AWSProtocol) = {
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
            protocol match {
              case AWSProtocol.`AWSJsonProtocol1.0` =>
                complete(ListQueuesResponse(queueNames.map(queueName => s"$baseURL/$queueName").toList))
              case _ =>
                respondWith {
                  <ListQueuesResponse>
                  <ListQueuesResult>
                    {queueNames.map(queueName => <QueueUrl>{baseURL + "/" + queueName}</QueueUrl>)}
                  </ListQueuesResult>
                  <ResponseMetadata>
                    <RequestId>{EmptyRequestId}</RequestId>
                  </ResponseMetadata>
                </ListQueuesResponse>
                }
            }
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

  implicit val format: RootJsonFormat[ListQueuesActionRequest] = jsonFormat3(ListQueuesActionRequest.apply)

  implicit val fpr: FlatParamsReader[ListQueuesActionRequest] = new FlatParamsReader[ListQueuesActionRequest] {
    override def read(params: Map[String, String]): ListQueuesActionRequest = {
      new ListQueuesActionRequest(
        params.get("MaxResults").flatMap(_.toIntOption),
        params.get("NextToken"),
        params.get("QueueNamePrefix")
      )
    }
  }

}

case class ListQueuesResponse(QueueUrls: List[String])

object ListQueuesResponse {
  implicit val format: RootJsonFormat[ListQueuesResponse] = jsonFormat1(ListQueuesResponse.apply)
}
