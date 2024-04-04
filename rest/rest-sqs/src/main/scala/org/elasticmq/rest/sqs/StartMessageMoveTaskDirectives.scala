package org.elasticmq.rest.sqs

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.server.Route
import org.elasticmq.ElasticMQError
import org.elasticmq.actor.reply._
import org.elasticmq.msg.StartMessageMoveTask
import org.elasticmq.rest.sqs.Action.{StartMessageMoveTask => StartMessageMoveTaskAction}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.async.Async._

trait StartMessageMoveTaskDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>

  private val Arn = "(?:.+:(.+)?:(.+)?:)?(.+)".r

  def startMessageMoveTask(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(StartMessageMoveTaskAction) {
      val params = p.as[StartMessageMoveTaskActionRequest]
      val sourceQueueName = arnToQueueName(params.SourceArn)
      queueActorAndDataFromQueueName(sourceQueueName) { (sourceQueue, _) =>
        params.DestinationArn match {
          case Some(destinationQueueArn) =>
            val destinationQueueName = arnToQueueName(destinationQueueArn)
            queueActorAndDataFromQueueName(destinationQueueName) { (destinationQueue, _) =>
              startMessageMoveTask(sourceQueue, Some(destinationQueue), params.MaxNumberOfMessagesPerSecond)
            }
          case None => startMessageMoveTask(sourceQueue, None, params.MaxNumberOfMessagesPerSecond)
        }
      }
    }
  }

  private def arnToQueueName(arn: String): String =
    arn match {
      case Arn(_, _, queueName) => queueName
      case _                    => throw new SQSException("InvalidParameterValue")
    }

  private def startMessageMoveTask(
      sourceQueue: ActorRef,
      destinationQueue: Option[ActorRef],
      maxNumberOfMessagesPerSecond: Option[Int]
  )(implicit marshallerDependencies: MarshallerDependencies): Route =
    async {
      await(
        queueManagerActor ? StartMessageMoveTask(sourceQueue, destinationQueue, maxNumberOfMessagesPerSecond)
      ) match {
        case Left(e: ElasticMQError) => throw new SQSException(e.code, errorMessage = Some(e.message))
        case Right(taskHandle)       => complete(StartMessageMoveTaskResponse(taskHandle))
      }
    }
}

case class StartMessageMoveTaskActionRequest(
    SourceArn: String,
    DestinationArn: Option[String],
    MaxNumberOfMessagesPerSecond: Option[Int]
)

object StartMessageMoveTaskActionRequest {
  implicit val requestJsonFormat: RootJsonFormat[StartMessageMoveTaskActionRequest] = jsonFormat3(
    StartMessageMoveTaskActionRequest.apply
  )

  implicit val requestParamReader: FlatParamsReader[StartMessageMoveTaskActionRequest] =
    new FlatParamsReader[StartMessageMoveTaskActionRequest] {
      override def read(params: Map[String, String]): StartMessageMoveTaskActionRequest = {
        new StartMessageMoveTaskActionRequest(
          requiredParameter(params)(SourceArnParameter),
          optionalParameter(params)(DestinationArnParameter),
          optionalParameter(params)(MaxNumberOfMessagesPerSecondParameter).map(_.toInt)
        )
      }
    }
}

case class StartMessageMoveTaskResponse(TaskHandle: String)

object StartMessageMoveTaskResponse {
  implicit val format: RootJsonFormat[StartMessageMoveTaskResponse] = jsonFormat1(StartMessageMoveTaskResponse.apply)

  implicit val xmlSerializer: XmlSerializer[StartMessageMoveTaskResponse] = t => <StartMessageMoveTaskResponse>
    <StartMessageMoveTaskResult>
      <TaskHandle>{t.TaskHandle}</TaskHandle>
    </StartMessageMoveTaskResult>
    <ResponseMetadata>
      <RequestId>{EmptyRequestId}</RequestId>
    </ResponseMetadata>
  </StartMessageMoveTaskResponse>
}
