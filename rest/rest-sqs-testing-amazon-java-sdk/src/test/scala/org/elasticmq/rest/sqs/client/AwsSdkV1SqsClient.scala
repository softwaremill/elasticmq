package org.elasticmq.rest.sqs.client

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{BatchResultErrorEntry, CancelMessageMoveTaskRequest, CreateQueueRequest, DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, GetQueueAttributesRequest, GetQueueUrlRequest, ListMessageMoveTasksRequest, MessageAttributeValue, MessageSystemAttributeValue, PurgeQueueRequest, QueueDoesNotExistException, ReceiveMessageRequest, ResourceNotFoundException, SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageRequest, StartMessageMoveTaskRequest, UnsupportedOperationException}
import org.elasticmq._

import java.nio.ByteBuffer
import java.util
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

  override def purgeQueue(
      queueUrl: QueueUrl
  ): Either[SqsClientError, Unit] = interceptErrors {
    client.purgeQueue(new PurgeQueueRequest().withQueueUrl(queueUrl))
  }

  override def deleteQueue(
      queueUrl: QueueUrl
  ): Either[SqsClientError, Unit] = interceptErrors {
    client.deleteQueue(queueUrl)
  }

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      awsTraceHeader: Option[String] = None
  ): Either[SqsClientError, Unit] = interceptErrors {
    client.sendMessage(
      new SendMessageRequest()
        .withQueueUrl(queueUrl)
        .withMessageBody(messageBody)
        .withMessageSystemAttributes(
          mapAwsTraceHeader(awsTraceHeader)
        )
        .withMessageAttributes(mapMessageAttributes(messageAttributes))
    )
  }

  override def sendMessageBatch(
      queueUrl: QueueUrl,
      entries: List[SendMessageBatchEntry]
  ): Either[SqsClientError, SendMessageBatchResult] = interceptErrors {
    val result = client.sendMessageBatch(
      new SendMessageBatchRequest()
        .withQueueUrl(queueUrl)
        .withEntries(
          entries
            .map(entry =>
              new SendMessageBatchRequestEntry()
                .withId(entry.id)
                .withMessageBody(entry.messageBody)
                .withDelaySeconds(entry.delaySeconds.map(Int.box).orNull)
                .withMessageDeduplicationId(entry.messageDeduplicationId.orNull)
                .withMessageGroupId(entry.messageGroupId.orNull)
                .withMessageSystemAttributes(mapAwsTraceHeader(entry.awsTraceHeader))
                .withMessageAttributes(mapMessageAttributes(entry.messageAttributes))
            )
            .asJava
        )
    )
    SendMessageBatchResult(
      result.getSuccessful.asScala.map { entry =>
        SendMessageBatchSuccessEntry(
          entry.getId,
          entry.getMessageId,
          entry.getMD5OfMessageBody,
          Option(entry.getMD5OfMessageAttributes),
          Option(entry.getMD5OfMessageSystemAttributes),
          Option(entry.getSequenceNumber)
        )
      }.toList,
      mapBatchResultErrorEntries(result.getFailed)
    )
  }

  override def deleteMessageBatch(
      queueUrl: QueueUrl,
      entries: List[DeleteMessageBatchEntry]
  ): Either[
    SqsClientError,
    DeleteMessageBatchResult
  ] = interceptErrors {
    val result = client.deleteMessageBatch(
      new DeleteMessageBatchRequest()
        .withQueueUrl(queueUrl)
        .withEntries(
          entries
            .map(entry =>
              new DeleteMessageBatchRequestEntry()
                .withId(entry.id)
                .withReceiptHandle(entry.receiptHandle)
            )
            .asJava
        )
    )
    DeleteMessageBatchResult(
      result.getSuccessful.asScala.map { entry =>
        DeleteMessageBatchSuccessEntry(entry.getId)
      }.toList,
      mapBatchResultErrorEntries(result.getFailed)
    )
  }

  private def mapBatchResultErrorEntries(
      failed: util.List[BatchResultErrorEntry]
  ) = {
    failed.asScala.map { entry =>
      BatchOperationErrorEntry(
        entry.getId,
        entry.isSenderFault,
        entry.getCode,
        entry.getMessage
      )
    }.toList
  }

  override def deleteMessage(
      queueUrl: QueueUrl,
      receiptHandle: MessageMoveTaskStatus
  ): Unit = client.deleteMessage(queueUrl, receiptHandle)

  private def mapAwsTraceHeader(awsTraceHeader: Option[MessageMoveTaskStatus]) = {
    awsTraceHeader
      .map(header => Map("AWSTraceHeader" -> new MessageSystemAttributeValue().withStringValue(header)).asJava)
      .orNull
  }

  private def mapMessageAttributes(
      messageAttributes: Map[
        MessageMoveTaskStatus,
        MessageAttribute
      ]
  ) = {
    messageAttributes.map {
      case (k, v: StringMessageAttribute) =>
        k -> new MessageAttributeValue().withDataType(v.getDataType()).withStringValue(v.stringValue)
      case (k, v: NumberMessageAttribute) =>
        k -> new MessageAttributeValue().withDataType(v.getDataType()).withStringValue(v.stringValue)
      case (k, v: BinaryMessageAttribute) =>
        k -> new MessageAttributeValue()
          .withDataType(v.getDataType())
          .withBinaryValue(ByteBuffer.wrap(v.binaryValue.toArray))
    }.asJava
  }

  override def receiveMessage(
      queueUrl: QueueUrl,
      systemAttributes: List[String] = List.empty,
      messageAttributes: List[String] = List.empty,
      maxNumberOfMessages: Option[Int] = None
  ): List[ReceivedMessage] = {
    client
      .receiveMessage(
        new ReceiveMessageRequest()
          .withQueueUrl(queueUrl)
          .withAttributeNames(systemAttributes.asJava)
          .withMessageAttributeNames(messageAttributes.asJava)
          .withMaxNumberOfMessages(maxNumberOfMessages.map(Int.box).orNull)
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

  override def tagQueue(queueUrl: QueueUrl, tags: Map[String, String]): Unit = {
    client.tagQueue(queueUrl, tags.asJava)
  }

  override def untagQueue(queueUrl: QueueUrl, tagKeys: List[String]): Unit = {
    client.untagQueue(queueUrl, tagKeys.asJava)
  }

  override def listQueueTags(
      queueUrl: QueueUrl
  ): Map[String, String] = client.listQueueTags(queueUrl).getTags.asScala.toMap

  override def listQueues(
      prefix: Option[String]
  ): List[QueueUrl] = client
    .listQueues(prefix.orNull)
    .getQueueUrls
    .asScala
    .toList

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

  override def addPermission(
      queueUrl: QueueUrl,
      label: MessageMoveTaskStatus,
      awsAccountIds: List[MessageMoveTaskStatus],
      actions: List[MessageMoveTaskStatus]
  ): Unit = client.addPermission(queueUrl, label, awsAccountIds.asJava, actions.asJava)

  override def removePermission(
      queueUrl: QueueUrl,
      label: MessageMoveTaskStatus
  ): Unit = client.removePermission(queueUrl, label)

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
