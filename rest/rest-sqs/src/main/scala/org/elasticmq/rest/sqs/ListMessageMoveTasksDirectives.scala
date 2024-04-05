package org.elasticmq.rest.sqs

import org.apache.pekko.http.scaladsl.server.Route
import org.elasticmq.actor.reply._
import org.elasticmq.msg.GetMovingMessagesTasks
import org.elasticmq.rest.sqs.Action.{ListMessageMoveTasks => ListMessageMoveTasksAction}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

trait ListMessageMoveTasksDirectives extends ArnSupport {
  this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>

  def listMessageMoveTasks(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies): Route = {
    p.action(ListMessageMoveTasksAction) {
      val params = p.as[ListMessageMoveTasksRequest]
      val sourceQueueName = extractQueueName(params.SourceArn)
      queueActorAndDataFromQueueName(sourceQueueName) { (sourceQueue, _) =>
        for {
          tasks <- sourceQueue ? GetMovingMessagesTasks()
        } yield {
          complete {
            ListMessageMoveTasksResponse(
              tasks.map(task =>
                MessageMoveTaskResponse(
                  task.numberOfMessagesMoved,
                  task.numberOfMessagesToMove,
                  task.destinationArn,
                  None,
                  task.maxNumberOfMessagesPerSecond,
                  task.sourceArn,
                  task.startedTimestamp,
                  task.status,
                  task.taskHandle
                )
              )
            )
          }
        }
      }
    }
  }
}

case class ListMessageMoveTasksRequest(
    MaxResults: Option[Int],
    SourceArn: String
)

object ListMessageMoveTasksRequest {
  implicit val requestJsonFormat: RootJsonFormat[ListMessageMoveTasksRequest] = jsonFormat2(
    ListMessageMoveTasksRequest.apply
  )

  implicit val requestParamReader: FlatParamsReader[ListMessageMoveTasksRequest] =
    new FlatParamsReader[ListMessageMoveTasksRequest] {
      override def read(params: Map[String, String]): ListMessageMoveTasksRequest = {
        new ListMessageMoveTasksRequest(
          optionalParameter(params)(MaxResultsParameter).map(_.toInt),
          requiredParameter(params)(SourceArnParameter)
        )
      }
    }
}

case class MessageMoveTaskResponse(
    ApproximateNumberOfMessagesMoved: Long,
    ApproximateNumberOfMessagesToMove: Long,
    DestinationArn: Option[String],
    FailureReason: Option[String],
    MaxNumberOfMessagesPerSecond: Option[Int],
    SourceArn: String,
    StartedTimestamp: Long,
    Status: String,
    TaskHandle: String
)
case class ListMessageMoveTasksResponse(Results: List[MessageMoveTaskResponse])

object ListMessageMoveTasksResponse {
  implicit val format: RootJsonFormat[MessageMoveTaskResponse] = jsonFormat9(MessageMoveTaskResponse.apply)
  implicit val formatList: RootJsonFormat[ListMessageMoveTasksResponse] = jsonFormat1(
    ListMessageMoveTasksResponse.apply
  )

  implicit val xmlSerializer: XmlSerializer[ListMessageMoveTasksResponse] = t =>
    <ListMessageMoveTasksResponse>
    <ListMessageMoveTasksResult>{
      t.Results.map(task =>
        <ListMessageMoveTasksResultEntry>
          <ApproximateNumberOfMessagesMoved>{task.ApproximateNumberOfMessagesMoved}</ApproximateNumberOfMessagesMoved>
          <ApproximateNumberOfMessagesToMove>{
          task.ApproximateNumberOfMessagesToMove
        }</ApproximateNumberOfMessagesToMove>
          {task.DestinationArn.map(arn => <DestinationArn>{arn}</DestinationArn>).getOrElse("")}
          {
          task.MaxNumberOfMessagesPerSecond
            .map(v => <MaxNumberOfMessagesPerSecond>{v}</MaxNumberOfMessagesPerSecond>)
            .getOrElse("")
        }
          <SourceArn>{task.SourceArn}</SourceArn>
          <StartedTimestamp>{task.StartedTimestamp}</StartedTimestamp>
          <Status>{task.Status}</Status>
          <TaskHandle>{task.TaskHandle}</TaskHandle>
        </ListMessageMoveTasksResultEntry>
      )
    }
    </ListMessageMoveTasksResult>
    <ResponseMetadata>
      <RequestId>{EmptyRequestId}</RequestId>
    </ResponseMetadata>
  </ListMessageMoveTasksResponse>
}
