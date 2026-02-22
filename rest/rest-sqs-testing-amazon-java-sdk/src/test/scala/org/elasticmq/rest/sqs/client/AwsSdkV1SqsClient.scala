package org.elasticmq.rest.sqs.client

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.{
  BatchResultErrorEntry,
  CancelMessageMoveTaskRequest,
  ChangeMessageVisibilityBatchRequest,
  ChangeMessageVisibilityBatchRequestEntry,
  CreateQueueRequest,
  DeleteMessageBatchRequest,
  DeleteMessageBatchRequestEntry,
  GetQueueAttributesRequest,
  GetQueueUrlRequest,
  ListDeadLetterSourceQueuesRequest,
  ListMessageMoveTasksRequest,
  MessageAttributeValue,
  MessageSystemAttributeValue,
  PurgeQueueRequest,
  QueueDoesNotExistException,
  ReceiveMessageRequest,
  ResourceNotFoundException,
  SendMessageBatchRequest,
  SendMessageBatchRequestEntry,
  SendMessageRequest,
  StartMessageMoveTaskRequest,
  UnsupportedOperationException
}
import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}

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
  ): Either[SqsClientError, QueueUrl] = interceptCreateQueue {
    client
      .createQueue(
        new CreateQueueRequest()
          .withQueueName(queueName)
          .withAttributes(attributes.map { case (k, v) => (k.value, v) }.asJava)
      )
      .getQueueUrl
  }

  private def interceptCreateQueue[T](f: => T): Either[SqsClientError, T] = {
    try {
      Right(f)
    } catch {
      case e: com.amazonaws.services.sqs.model.InvalidAttributeNameException =>
        Left(SqsClientError(InvalidAttributeName, e.getErrorMessage))
      case e: Exception => interceptErrors(f)
    }
  }

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
      delaySeconds: Option[Int] = None,
      messageAttributes: Map[
        String,
        MessageAttribute
      ] = Map.empty,
      awsTraceHeader: Option[String] = None,
      messageGroupId: Option[String] = None,
      messageDeduplicationId: Option[String] = None,
      customHeaders: Map[String, String] = Map.empty
  ): Either[SqsClientError, SendMessageResult] = interceptErrors {
    val request = new SendMessageRequest()
      .withQueueUrl(queueUrl)
      .withMessageBody(messageBody)
      .withDelaySeconds(delaySeconds.map(Int.box).orNull)
      .withMessageSystemAttributes(
        mapAwsTraceHeader(awsTraceHeader)
      )
      .withMessageAttributes(mapMessageAttributes(messageAttributes))
      .withMessageGroupId(messageGroupId.orNull)
      .withMessageDeduplicationId(messageDeduplicationId.orNull)

    customHeaders.foreach { case (k, v) => request.putCustomRequestHeader(k, v) }

    val result = client.sendMessage(request)
    SendMessageResult(
      result.getMessageId,
      result.getMD5OfMessageBody,
      Option(result.getMD5OfMessageAttributes),
      Option(result.getSequenceNumber)
    )
  }

  override def sendMessageBatch(
      queueUrl: QueueUrl,
      entries: List[SendMessageBatchEntry],
      customHeaders: Map[String, String] = Map.empty
  ): Either[SqsClientError, SendMessageBatchResult] = interceptErrors {
    val request = new SendMessageBatchRequest()
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

    customHeaders.foreach { case (k, v) => request.putCustomRequestHeader(k, v) }

    val result = client.sendMessageBatch(request)
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

  override def changeMessageVisibility(
      queueUrl: QueueUrl,
      receiptHandle: MessageMoveTaskStatus,
      visibilityTimeout: Int
  ): Unit = client.changeMessageVisibility(queueUrl, receiptHandle, visibilityTimeout)

  override def changeMessageVisibilityBatch(
      queueUrl: QueueUrl,
      entries: List[
        ChangeMessageVisibilityBatchEntry
      ]
  ): Either[
    SqsClientError,
    ChangeMessageVisibilityBatchResult
  ] = interceptErrors {
    val result = client.changeMessageVisibilityBatch(
      new ChangeMessageVisibilityBatchRequest()
        .withQueueUrl(queueUrl)
        .withEntries(
          entries
            .map(entry =>
              new ChangeMessageVisibilityBatchRequestEntry()
                .withId(entry.id)
                .withReceiptHandle(entry.receiptHandle)
                .withVisibilityTimeout(entry.visibilityTimeout)
            )
            .asJava
        )
    )
    ChangeMessageVisibilityBatchResult(
      result.getSuccessful.asScala.map { entry =>
        ChangeMessageVisibilityBatchSuccessEntry(entry.getId)
      }.toList,
      mapBatchResultErrorEntries(result.getFailed)
    )
  }

  override def setQueueAttributes(queueUrl: QueueUrl, attributes: Map[QueueAttributeName, String]): Unit =
    client.setQueueAttributes(
      new com.amazonaws.services.sqs.model.SetQueueAttributesRequest()
        .withQueueUrl(queueUrl)
        .withAttributes(attributes.map { case (k, v) => (k.value, v) }.asJava)
    )

  override def listDeadLetterSourceQueues(queueUrl: QueueUrl): List[QueueUrl] = client
    .listDeadLetterSourceQueues(new ListDeadLetterSourceQueuesRequest().withQueueUrl(queueUrl))
    .getQueueUrls
    .asScala
    .toList

  private def mapAwsTraceHeader(awsTraceHeader: Option[MessageMoveTaskStatus]) = {
    awsTraceHeader
      .map(header =>
        Map("AWSTraceHeader" -> new MessageSystemAttributeValue().withStringValue(header).withDataType("String")).asJava
      )
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
      case e: com.amazonaws.services.sqs.model.AmazonSQSException
          if e.getErrorCode == "InvalidParameterValue" || e.getErrorCode == "InvalidAttributeValue" || e.getErrorCode == "InvalidAttributeName" =>
        Left(SqsClientError(InvalidParameterValue, e.getErrorMessage))
      case e: com.amazonaws.services.sqs.model.AmazonSQSException if e.getErrorCode == "MissingParameter" =>
        Left(SqsClientError(MissingParameter, e.getErrorMessage))
      case e: com.amazonaws.AmazonClientException if e.getMessage.contains("MD5 returned by SQS does not match") =>
        Left(SqsClientError(InvalidParameterValue, e.getMessage))
      case e: Exception =>
        Left(SqsClientError(UnknownSqsClientErrorType, e.getMessage))
    }
  }
}
