package org.elasticmq.rest.sqs.client
import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.model.{AddPermissionRequest, BatchResultErrorEntry, CancelMessageMoveTaskRequest, ChangeMessageVisibilityBatchRequest, ChangeMessageVisibilityBatchRequestEntry, ChangeMessageVisibilityRequest, CreateQueueRequest, DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, DeleteMessageRequest, DeleteQueueRequest, GetQueueAttributesRequest, GetQueueUrlRequest, ListDeadLetterSourceQueuesRequest, ListMessageMoveTasksRequest, ListQueueTagsRequest, ListQueuesRequest, MessageAttributeValue, MessageSystemAttributeNameForSends, MessageSystemAttributeValue, PurgeQueueRequest, QueueDoesNotExistException, ReceiveMessageRequest, RemovePermissionRequest, ResourceNotFoundException, SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageRequest, StartMessageMoveTaskRequest, TagQueueRequest, UnsupportedOperationException, UntagQueueRequest, MessageSystemAttributeName => SdkMessageSystemAttributeName, QueueAttributeName => AwsQueueAttributeName}

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

  override def purgeQueue(queueUrl: QueueUrl): Either[SqsClientError, Unit] = interceptErrors {
    client.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
  }

  override def deleteQueue(queueUrl: QueueUrl): Either[SqsClientError, Unit] = interceptErrors {
    client.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build())
  }

  override def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      awsTraceHeader: Option[String] = None
  ): Either[SqsClientError, Unit] = interceptErrors {
    client.sendMessage(
      SendMessageRequest
        .builder()
        .queueUrl(queueUrl)
        .messageBody(messageBody)
        .messageSystemAttributes(mapAwsTraceHeader(awsTraceHeader))
        .messageAttributes(mapMessageAttributes(messageAttributes))
        .build()
    )
  }

  private def mapAwsTraceHeader(awsTraceHeader: Option[String]) = {
    awsTraceHeader
      .map(header =>
        Map(
          MessageSystemAttributeNameForSends.AWS_TRACE_HEADER -> MessageSystemAttributeValue
            .builder()
            .dataType("String")
            .stringValue(header)
            .build()
        )
      )
      .getOrElse(Map.empty)
      .asJava
  }

  private def mapMessageAttributes(
      messageAttributes: Map[
        String,
        MessageAttribute
      ]
  ) = {
    messageAttributes.map {
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
    }.asJava
  }

  override def sendMessageBatch(
      queueUrl: QueueUrl,
      entries: List[SendMessageBatchEntry]
  ): Either[SqsClientError, SendMessageBatchResult] = interceptErrors {
    val result = client.sendMessageBatch(
      SendMessageBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          entries
            .map(entry =>
              SendMessageBatchRequestEntry
                .builder()
                .id(entry.id)
                .messageBody(entry.messageBody)
                .delaySeconds(entry.delaySeconds.map(Int.box).orNull)
                .messageDeduplicationId(entry.messageDeduplicationId.orNull)
                .messageGroupId(entry.messageGroupId.orNull)
                .messageSystemAttributes(mapAwsTraceHeader(entry.awsTraceHeader))
                .messageAttributes(mapMessageAttributes(entry.messageAttributes))
                .build()
            )
            .asJava
        )
        .build()
    )
    SendMessageBatchResult(
      result.successful().asScala.toList.map { entry =>
        SendMessageBatchSuccessEntry(
          entry.id(),
          entry.messageId(),
          entry.md5OfMessageBody(),
          Option(entry.md5OfMessageAttributes()),
          Option(entry.md5OfMessageSystemAttributes()),
          Option(entry.sequenceNumber())
        )
      },
      mapBatchResultErrorEntries(result.failed())
    )
  }

  private def mapBatchResultErrorEntries(failed: java.util.List[BatchResultErrorEntry]) = {
    failed.asScala.toList.map { entry =>
      BatchOperationErrorEntry(
        entry.id(),
        entry.senderFault(),
        entry.code(),
        entry.message()
      )
    }
  }

  override def deleteMessageBatch(
      queueUrl: QueueUrl,
      entries: List[DeleteMessageBatchEntry]
  ): Either[SqsClientError, DeleteMessageBatchResult] = interceptErrors {
    val result = client.deleteMessageBatch(
      DeleteMessageBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          entries
            .map(entry =>
              DeleteMessageBatchRequestEntry
                .builder()
                .id(entry.id)
                .receiptHandle(entry.receiptHandle)
                .build()
            )
            .asJava
        )
        .build()
    )
    DeleteMessageBatchResult(
      result.successful().asScala.toList.map { entry =>
        DeleteMessageBatchSuccessEntry(entry.id())
      },
      mapBatchResultErrorEntries(result.failed())
    )
  }

  override def receiveMessage(
      queueUrl: QueueUrl,
      systemAttributes: List[String] = List.empty,
      messageAttributes: List[String] = List.empty,
      maxNumberOfMessages: Option[Int] = None
  ): List[ReceivedMessage] =
    client
      .receiveMessage(
        ReceiveMessageRequest
          .builder()
          .queueUrl(queueUrl)
          .attributeNamesWithStrings(systemAttributes.asJava)
          .messageAttributeNames(messageAttributes.asJava)
          .maxNumberOfMessages(maxNumberOfMessages.map(Int.box).orNull)
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

  override def deleteMessage(queueUrl: QueueUrl, receiptHandle: String): Unit =
    client.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build())

  override def changeMessageVisibility(queueUrl: QueueUrl, receiptHandle: String, visibilityTimeout: Int): Unit =
    client.changeMessageVisibility(
      ChangeMessageVisibilityRequest
        .builder()
        .queueUrl(queueUrl)
        .receiptHandle(receiptHandle)
        .visibilityTimeout(visibilityTimeout)
        .build()
    )

  override def changeMessageVisibilityBatch(
      queueUrl: QueueUrl,
      entries: List[ChangeMessageVisibilityBatchEntry]
  ): Either[SqsClientError, ChangeMessageVisibilityBatchResult] = interceptErrors {
    val result = client.changeMessageVisibilityBatch(
      ChangeMessageVisibilityBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          entries
            .map(entry =>
              ChangeMessageVisibilityBatchRequestEntry
                .builder()
                .id(entry.id)
                .receiptHandle(entry.receiptHandle)
                .visibilityTimeout(entry.visibilityTimeout)
                .build()
            )
            .asJava
        )
        .build()
    )
    ChangeMessageVisibilityBatchResult(
      result.successful().asScala.toList.map { entry =>
        ChangeMessageVisibilityBatchSuccessEntry(entry.id())
      },
      mapBatchResultErrorEntries(result.failed())
    )
  }

  override def listDeadLetterSourceQueues(queueUrl: QueueUrl): List[QueueUrl] =
    client
      .listDeadLetterSourceQueues(ListDeadLetterSourceQueuesRequest.builder().queueUrl(queueUrl).build())
      .queueUrls()
      .asScala
      .toList

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

  override def tagQueue(queueUrl: QueueUrl, tags: Map[String, String]): Unit = {
    client.tagQueue(
      TagQueueRequest
        .builder()
        .queueUrl(queueUrl)
        .tags(tags.asJava)
        .build()
    )
  }

  override def untagQueue(
      queueUrl: QueueUrl,
      tagKeys: List[MessageMoveTaskStatus]
  ): Unit = {
    client.untagQueue(
      UntagQueueRequest
        .builder()
        .queueUrl(queueUrl)
        .tagKeys(tagKeys.asJava)
        .build()
    )
  }

  override def listQueueTags(queueUrl: QueueUrl): Map[String, String] = {
    client
      .listQueueTags(
        ListQueueTagsRequest
          .builder()
          .queueUrl(queueUrl)
          .build()
      )
      .tags()
      .asScala
      .toMap
  }

  override def listQueues(
      prefix: Option[MessageMoveTaskStatus]
  ): List[QueueUrl] = {
    client
      .listQueues(
        ListQueuesRequest
          .builder()
          .queueNamePrefix(prefix.orNull)
          .build()
      )
      .queueUrls()
      .asScala
      .toList
  }

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

  override def addPermission(
      queueUrl: QueueUrl,
      label: String,
      awsAccountIds: List[String],
      actions: List[String]
  ): Unit =
    client
      .addPermission(
        AddPermissionRequest
          .builder()
          .queueUrl(queueUrl)
          .label(label)
          .awsAccountIds(awsAccountIds.asJava)
          .actions(actions.asJava)
          .build()
      )

  override def removePermission(queueUrl: QueueUrl, label: String): Unit =
    client
      .removePermission(
        RemovePermissionRequest
          .builder()
          .queueUrl(queueUrl)
          .label(label)
          .build()
      )

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
