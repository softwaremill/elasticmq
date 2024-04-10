package org.elasticmq.rest.sqs.client

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{CancelMessageMoveTaskRequest, CreateQueueRequest, GetQueueAttributesRequest, ListMessageMoveTasksRequest, QueueDoesNotExistException, ReceiveMessageRequest, ResourceNotFoundException, SendMessageRequest, StartMessageMoveTaskRequest, UnsupportedOperationException}

import scala.collection.JavaConverters._

class AwsSdkV1SqsClient(client: AmazonSQS) extends SqsClient {

  override def createQueue(
      queueName: String,
      attributes: Map[
        QueueAttributeName,
        String
      ] = Map.empty
  ): QueueUrl = client
    .createQueue(
      new CreateQueueRequest()
        .withQueueName(queueName)
        .withAttributes(attributes.map { case (k, v) => (k.value, v) }.asJava)
    )
    .getQueueUrl

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String
  ): Unit = client.sendMessage(
    new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(messageBody)
  )

  override def receiveMessage(queueUrl: QueueUrl): List[ReceivedMessage] =
    client.receiveMessage(new ReceiveMessageRequest().withQueueUrl(queueUrl)).getMessages.asScala.toList.map { msg =>
      ReceivedMessage(msg.getMessageId, msg.getReceiptHandle, msg.getBody)
    }

  override def getQueueAttributes(
      queueUrl: QueueUrl,
      attributeNames: QueueAttributeName*
  ): Map[String, String] = client
    .getQueueAttributes(
      new GetQueueAttributesRequest()
        .withQueueUrl(queueUrl)
        .withAttributeNames(attributeNames.toList.map(_.value).asJava)
    )
    .getAttributes
    .asScala
    .toMap

  override def startMessageMoveTask(
      sourceArn: Arn,
      maxNumberOfMessagesPerSecond: Option[Int]
  ): Either[SqsClientError, TaskHandle] = interceptErrors {
    client
      .startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(sourceArn)
          .withMaxNumberOfMessagesPerSecond(maxNumberOfMessagesPerSecond match {
            case Some(value) => value
            case None        => null
          })
      )
      .getTaskHandle
  }

  override def listMessageMoveTasks(
      sourceArn: Arn,
      maxResults: Option[Int]
  ): Either[SqsClientError, List[MessageMoveTask]] = interceptErrors {
    client
      .listMessageMoveTasks(
        new ListMessageMoveTasksRequest()
          .withSourceArn(sourceArn)
          .withMaxResults(maxResults match {
            case Some(value) => value
            case None        => null
          })
      )
      .getResults
      .asScala
      .toList
      .map { task =>
        MessageMoveTask(
          task.getTaskHandle,
          task.getSourceArn,
          task.getStatus,
          Option(task.getMaxNumberOfMessagesPerSecond).map(_.intValue())
        )
      }
  }

  override def cancelMessageMoveTask(
      taskHandle: TaskHandle
  ): Either[SqsClientError, ApproximateNumberOfMessagesMoved] = interceptErrors {
    client
      .cancelMessageMoveTask(new CancelMessageMoveTaskRequest().withTaskHandle(taskHandle))
      .getApproximateNumberOfMessagesMoved
  }

  private def interceptErrors[T](f: => T): Either[SqsClientError, T] = {
    try {
      Right(f)
    } catch {
      case e: UnsupportedOperationException =>
        Left(SqsClientError(UnsupportedOperation, e.getErrorMessage))
      case e: ResourceNotFoundException =>
        Left(SqsClientError(ResourceNotFound, e.getErrorMessage))
      case e: QueueDoesNotExistException =>
        Left(SqsClientError(QueueDoesNotExist, e.getErrorMessage))
      case e: Exception => Left(SqsClientError(UnknownSqsClientErrorType, e.getMessage))
    }
  }
}
