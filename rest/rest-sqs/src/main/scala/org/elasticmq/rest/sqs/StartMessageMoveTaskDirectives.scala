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

trait StartMessageMoveTaskDirectives extends ArnSupport {
  this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>

  def startMessageMoveTask(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(StartMessageMoveTaskAction) {
      val params = p.as[StartMessageMoveTaskActionRequest]
      val sourceQueueName = extractQueueName(params.SourceArn)
      queueActorAndDataFromQueueName(sourceQueueName) { (sourceQueue, _) =>
        params.DestinationArn match {
          case Some(destinationQueueArn) =>
            val destinationQueueName = extractQueueName(destinationQueueArn)
            queueActorAndDataFromQueueName(destinationQueueName) { (destinationQueue, _) =>
              startMessageMoveTask(sourceQueue, params.SourceArn, Some(destinationQueue), params.DestinationArn, params.MaxNumberOfMessagesPerSecond)
            }
          case None => startMessageMoveTask(sourceQueue, params.SourceArn, None, None, params.MaxNumberOfMessagesPerSecond)
        }
      }
    }
  }

  private def startMessageMoveTask(
      sourceQueue: ActorRef,
      sourceArn: String,
      destinationQueue: Option[ActorRef],
      destinationArn: Option[String],
      maxNumberOfMessagesPerSecond: Option[Int]
  )(implicit marshallerDependencies: MarshallerDependencies): Route = {
    for {
      res <- queueManagerActor ? StartMessageMoveTask(
        sourceQueue,
        sourceArn,
        destinationQueue,
        destinationArn,
        maxNumberOfMessagesPerSecond
      )
    } yield {
      res match {
        case Left(e: ElasticMQError) => throw new SQSException(e.code, errorMessage = Some(e.message))
        case Right(taskHandle)       => complete(StartMessageMoveTaskResponse(taskHandle))
      }
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
