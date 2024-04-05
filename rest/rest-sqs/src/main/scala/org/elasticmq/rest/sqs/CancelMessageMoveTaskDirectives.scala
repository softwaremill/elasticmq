package org.elasticmq.rest.sqs

import org.apache.pekko.http.scaladsl.server.Route
import org.elasticmq.ElasticMQError
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CancelMessageMoveTask
import org.elasticmq.rest.sqs.Action.{CancelMessageMoveTask => CancelMessageMoveTaskAction}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.async.Async._

trait CancelMessageMoveTaskDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>

  def cancelMessageMoveTask(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(CancelMessageMoveTaskAction) {
      val params = p.as[CancelMessageMoveTaskRequest]
      async {
        await(
          queueManagerActor ? CancelMessageMoveTask(params.TaskHandle)
        ) match {
          case Left(e: ElasticMQError) => throw new SQSException(e.code, errorMessage = Some(e.message))
          case Right(approximateNumberOfMessagesMoved) =>
            complete(CancelMessageMoveTaskResponse(approximateNumberOfMessagesMoved))
        }
      }
    }
  }
}

case class CancelMessageMoveTaskRequest(
    TaskHandle: String
)

object CancelMessageMoveTaskRequest {
  implicit val requestJsonFormat: RootJsonFormat[CancelMessageMoveTaskRequest] = jsonFormat1(
    CancelMessageMoveTaskRequest.apply
  )

  implicit val requestParamReader: FlatParamsReader[CancelMessageMoveTaskRequest] =
    new FlatParamsReader[CancelMessageMoveTaskRequest] {
      override def read(params: Map[String, String]): CancelMessageMoveTaskRequest = {
        new CancelMessageMoveTaskRequest(
          requiredParameter(params)(TaskHandleParameter)
        )
      }
    }
}

case class CancelMessageMoveTaskResponse(ApproximateNumberOfMessagesMoved: Long)

object CancelMessageMoveTaskResponse {
  implicit val format: RootJsonFormat[CancelMessageMoveTaskResponse] = jsonFormat1(CancelMessageMoveTaskResponse.apply)

  implicit val xmlSerializer: XmlSerializer[CancelMessageMoveTaskResponse] = t => <CancelMessageMoveTaskResponse>
    <CancelMessageMoveTaskResult>
      <ApproximateNumberOfMessagesMoved>{t.ApproximateNumberOfMessagesMoved}</ApproximateNumberOfMessagesMoved>
    </CancelMessageMoveTaskResult>
    <ResponseMetadata>
      <RequestId>{EmptyRequestId}</RequestId>
    </ResponseMetadata>
  </CancelMessageMoveTaskResponse>
}
