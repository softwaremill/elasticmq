package org.elasticmq.rest.sqs.client
import software.amazon.awssdk.services.sqs.model.{CancelMessageMoveTaskRequest, CreateQueueRequest, GetQueueAttributesRequest, ListMessageMoveTasksRequest, QueueDoesNotExistException, ReceiveMessageRequest, ResourceNotFoundException, SendMessageRequest, StartMessageMoveTaskRequest, UnsupportedOperationException, QueueAttributeName => AwsQueueAttributeName}

import scala.jdk.CollectionConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter, mapAsScalaMapConverter, seqAsJavaListConverter}

class AwsSdkV2SqsClient(client: software.amazon.awssdk.services.sqs.SqsClient) extends SqsClient {

  override def createQueue(
      queueName: String,
      attributes: Map[
        QueueAttributeName,
        String
      ] = Map.empty
  ): QueueUrl = client
    .createQueue(
      CreateQueueRequest
        .builder()
        .queueName(queueName)
        .attributes(attributes.map { case (k, v) => (AwsQueueAttributeName.fromValue(k.value), v) }.asJava)
        .build()
    )
    .queueUrl()

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String
  ): Unit = client.sendMessage(
    SendMessageRequest
      .builder()
      .queueUrl(queueUrl)
      .messageBody(messageBody)
      .build()
  )

  override def receiveMessage(queueUrl: QueueUrl): List[ReceivedMessage] =
    client.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queueUrl).build()).messages().asScala.toList.map {
      msg =>
        ReceivedMessage(msg.messageId(), msg.receiptHandle(), msg.body())
    }

  override def getQueueAttributes(
      queueUrl: QueueUrl,
      attributeNames: QueueAttributeName*
  ): Map[String, String] = client
    .getQueueAttributes(
      GetQueueAttributesRequest
        .builder()
        .queueUrl(queueUrl)
        .attributeNames(attributeNames.toList.map(atr => AwsQueueAttributeName.fromValue(atr.value)).asJava)
        .build()
    )
    .attributes()
    .asScala
    .map { case (k, v) => (k.toString, v) }
    .toMap

  override def startMessageMoveTask(
      sourceArn: Arn,
      maxNumberOfMessagesPerSecond: Option[Int]
  ): Either[SqsClientError, TaskHandle] = interceptErrors {
    client
      .startMessageMoveTask(
        StartMessageMoveTaskRequest
          .builder()
          .sourceArn(sourceArn)
          .maxNumberOfMessagesPerSecond(maxNumberOfMessagesPerSecond match {
            case Some(value) => value
            case None        => null
          })
          .build()
      )
      .taskHandle()
  }

  override def listMessageMoveTasks(
      sourceArn: Arn,
      maxResults: Option[Int]
  ): Either[SqsClientError, List[MessageMoveTask]] = interceptErrors {
    client
      .listMessageMoveTasks(
        ListMessageMoveTasksRequest
          .builder()
          .sourceArn(sourceArn)
          .maxResults(maxResults match {
            case Some(value) => value
            case None        => null
          })
          .build()
      )
      .results()
      .asScala
      .toList
      .map { task =>
        MessageMoveTask(
          task.taskHandle(),
          task.sourceArn(),
          task.status().toString,
          Option(task.maxNumberOfMessagesPerSecond()).map(_.intValue())
        )
      }
  }

  override def cancelMessageMoveTask(
      taskHandle: TaskHandle
  ): Either[SqsClientError, ApproximateNumberOfMessagesMoved] = interceptErrors {
    client
      .cancelMessageMoveTask(CancelMessageMoveTaskRequest.builder().taskHandle(taskHandle).build())
      .approximateNumberOfMessagesMoved()
  }

  private def interceptErrors[T](f: => T): Either[SqsClientError, T] = {
    try {
      Right(f)
    } catch {
      case e: UnsupportedOperationException =>
        Left(SqsClientError(UnsupportedOperation, e.awsErrorDetails().errorMessage()))
      case e: ResourceNotFoundException =>
        Left(SqsClientError(ResourceNotFound, e.awsErrorDetails().errorMessage()))
      case e: QueueDoesNotExistException =>
        Left(SqsClientError(QueueDoesNotExist, e.awsErrorDetails().errorMessage()))
      case e: Exception => Left(SqsClientError(UnknownSqsClientErrorType, e.getMessage))
    }
  }
}
