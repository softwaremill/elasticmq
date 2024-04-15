package org.elasticmq.rest.sqs.client

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{
  CancelMessageMoveTaskRequest,
  CreateQueueRequest,
  GetQueueAttributesRequest,
  GetQueueUrlRequest,
  ListMessageMoveTasksRequest,
  MessageAttributeValue,
  QueueDoesNotExistException,
  ReceiveMessageRequest,
  ResourceNotFoundException,
  SendMessageRequest,
  StartMessageMoveTaskRequest,
  UnsupportedOperationException
}
import org.elasticmq._

import java.nio.ByteBuffer
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

  override def getQueueUrl(queueName: String): Either[SqsClientError, QueueUrl] = interceptErrors {
    client
      .getQueueUrl(
        new GetQueueUrlRequest()
          .withQueueName(queueName)
      )
      .getQueueUrl
  }

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty
  ): Unit = client.sendMessage(
    new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(messageBody)
      .withMessageAttributes(messageAttributes.map {
        case (k, v: StringMessageAttribute) =>
          k -> new MessageAttributeValue().withDataType(v.getDataType()).withStringValue(v.stringValue)
        case (k, v: NumberMessageAttribute) =>
          k -> new MessageAttributeValue().withDataType(v.getDataType()).withStringValue(v.stringValue)
        case (k, v: BinaryMessageAttribute) =>
          k -> new MessageAttributeValue()
            .withDataType(v.getDataType())
            .withBinaryValue(ByteBuffer.wrap(v.binaryValue.toArray))
      }.asJava)
  )

  override def receiveMessage(
      queueUrl: QueueUrl,
      systemAttributes: List[String] = List.empty,
      messageAttributes: List[String] = List.empty
  ): List[ReceivedMessage] = {
    client
      .receiveMessage(
        new ReceiveMessageRequest()
          .withQueueUrl(queueUrl)
          .withAttributeNames(systemAttributes.asJava)
          .withMessageAttributeNames(messageAttributes.asJava)
      )
      .getMessages
      .asScala
      .toList
      .map { msg =>
        ReceivedMessage(
          msg.getMessageId,
          msg.getReceiptHandle,
          msg.getBody,
          mapSystemAttributes(msg.getAttributes),
          mapMessageAttributes(msg.getMessageAttributes)
        )
      }
  }

  private def mapSystemAttributes(
      attributes: java.util.Map[String, String]
  ): Map[MessageSystemAttributeName, String] = {
    attributes.asScala.toMap.map { case (k, v) => (MessageSystemAttributeName.from(k), v) }
  }

  private def mapMessageAttributes(
      attributes: java.util.Map[String, MessageAttributeValue]
  ): Map[String, MessageAttribute] = {
    attributes.asScala.toMap.map { case (k, v) => (k, mapMessageAttribute(v)) }
  }

  private def mapMessageAttribute(attr: MessageAttributeValue): MessageAttribute = {
    if (attr.getDataType.equals("String") && attr.getStringValue != null) {
      StringMessageAttribute(attr.getStringValue)
    } else if (attr.getDataType.startsWith("String.") && attr.getStringValue != null) {
      StringMessageAttribute(attr.getStringValue, Some(attr.getDataType.stripPrefix("String.")))
    } else if (attr.getDataType.equals("Number") && attr.getStringValue != null) {
      NumberMessageAttribute(attr.getStringValue)
    } else if (attr.getDataType.startsWith("Number.") && attr.getStringValue != null) {
      NumberMessageAttribute(attr.getStringValue, Some(attr.getDataType.stripPrefix("Number.")))
    } else {
      BinaryMessageAttribute.fromByteBuffer(attr.getBinaryValue)
    }
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
