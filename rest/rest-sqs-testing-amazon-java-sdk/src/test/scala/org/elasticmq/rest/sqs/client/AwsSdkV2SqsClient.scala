package org.elasticmq.rest.sqs.client
import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.model.{
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
  UnsupportedOperationException,
  MessageSystemAttributeName => SdkMessageSystemAttributeName,
  QueueAttributeName => AwsQueueAttributeName
}

import java.nio.ByteBuffer
import scala.collection.JavaConverters._

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

  override def getQueueUrl(queueName: String): Either[SqsClientError, QueueUrl] = interceptErrors {
      client
      .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
      .queueUrl()
    }

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty
  ): Unit = client.sendMessage(
    SendMessageRequest
      .builder()
      .queueUrl(queueUrl)
      .messageBody(messageBody)
      .messageAttributes(messageAttributes.map {
        case (k, v: StringMessageAttribute) =>
          k -> MessageAttributeValue.builder().dataType(v.getDataType()).stringValue(v.stringValue).build()
        case (k, v: NumberMessageAttribute) =>
          k -> MessageAttributeValue.builder().dataType(v.getDataType()).stringValue(v.stringValue).build()
        case (k, v: BinaryMessageAttribute) =>
          k -> MessageAttributeValue
            .builder()
            .dataType(v.getDataType())
            .binaryValue(SdkBytes.fromByteArray(v.binaryValue.toArray))
            .build()
      }.asJava)
      .build()
  )

  override def receiveMessage(
      queueUrl: QueueUrl,
      systemAttributes: List[String] = List.empty,
      messageAttributes: List[String] = List.empty
  ): List[ReceivedMessage] =
    client
      .receiveMessage(
        ReceiveMessageRequest
          .builder()
          .queueUrl(queueUrl)
          .attributeNames(systemAttributes.map(AwsQueueAttributeName.fromValue).asJava)
          .messageAttributeNames(messageAttributes.asJava)
          .build()
      )
      .messages()
      .asScala
      .toList
      .map { msg =>
        ReceivedMessage(
          msg.messageId(),
          msg.receiptHandle(),
          msg.body(),
          mapSystemAttributes(msg.attributes()),
          mapMessageAttributes(msg.messageAttributes())
        )
      }

  private def mapSystemAttributes(
      attributes: java.util.Map[SdkMessageSystemAttributeName, String]
  ): Map[MessageSystemAttributeName, String] = {
    attributes.asScala.toMap.map { case (k, v) => (MessageSystemAttributeName.from(k.toString), v) }
  }

  private def mapMessageAttributes(
      attributes: java.util.Map[String, MessageAttributeValue]
  ): Map[String, MessageAttribute] = {
    attributes.asScala.toMap.map { case (k, v) => (k, mapMessageAttribute(v)) }
  }

  private def mapMessageAttribute(attr: MessageAttributeValue): MessageAttribute = {
    if (attr.dataType().equals("String") && attr.stringValue() != null) {
      StringMessageAttribute(attr.stringValue())
    } else if (attr.dataType().startsWith("String.") && attr.stringValue() != null) {
      StringMessageAttribute(attr.stringValue(), Some(attr.dataType().stripPrefix("String.")))
    } else if (attr.dataType().equals("Number") && attr.stringValue() != null) {
      NumberMessageAttribute(attr.stringValue())
    } else if (attr.dataType().startsWith("Number.") && attr.stringValue() != null) {
      NumberMessageAttribute(attr.stringValue(), Some(attr.dataType().stripPrefix("Number.")))
    } else {
      BinaryMessageAttribute.fromByteBuffer(attr.binaryValue().asByteBuffer())
    }
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
