package org.elasticmq.rest.sqs.client
import org.elasticmq.MessageAttribute

trait SqsClient {

  def createQueue(queueName: String, attributes: Map[QueueAttributeName, String] = Map.empty): Either[SqsClientError, QueueUrl]
  def getQueueUrl(queueName: String): Either[SqsClientError, QueueUrl]
  def deleteQueue(queueUrl: QueueUrl): Either[SqsClientError, Unit]
  def purgeQueue(queueUrl: QueueUrl): Either[SqsClientError, Unit]
  def getQueueAttributes(queueUrl: QueueUrl, attributeNames: QueueAttributeName*): Map[String, String]
  def tagQueue(queueUrl: QueueUrl, tags: Map[String, String]): Unit
  def untagQueue(queueUrl: QueueUrl, tagKeys: List[String]): Unit
  def listQueueTags(queueUrl: QueueUrl): Map[String, String]

  def listQueues(prefix: Option[String] = None): List[QueueUrl]

  def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      delaySeconds: Option[Int] = None,
      messageAttributes: Map[
        String,
        MessageAttribute
      ] = Map.empty,
      awsTraceHeader: Option[String] = None,
      messageGroupId: Option[String] = None,
      messageDeduplicationId: Option[String] = None
  ): Either[SqsClientError, SendMessageResult]

  def receiveMessage(
      queueUrl: QueueUrl,
      systemAttributes: List[String] = List.empty,
      messageAttributes: List[String] = List.empty,
      maxNumberOfMessages: Option[Int] = None
  ): List[ReceivedMessage]

  def deleteMessage(queueUrl: QueueUrl, receiptHandle: String): Unit
  def changeMessageVisibility(queueUrl: QueueUrl, receiptHandle: String, visibilityTimeout: Int): Unit

  def sendMessageBatch(
      queueUrl: QueueUrl,
      entries: List[SendMessageBatchEntry]
  ): Either[SqsClientError, SendMessageBatchResult]

  def deleteMessageBatch(
      queueUrl: QueueUrl,
      entries: List[DeleteMessageBatchEntry]
  ): Either[SqsClientError, DeleteMessageBatchResult]

  def changeMessageVisibilityBatch(
      queueUrl: QueueUrl,
      entries: List[ChangeMessageVisibilityBatchEntry]
  ): Either[SqsClientError, ChangeMessageVisibilityBatchResult]

  def setQueueAttributes(queueUrl: QueueUrl, attributes: Map[QueueAttributeName, String]): Unit

  def startMessageMoveTask(
      sourceArn: Arn,
      maxNumberOfMessagesPerSecond: Option[Int] = None
  ): Either[SqsClientError, TaskHandle]
  def listMessageMoveTasks(
      sourceArn: Arn,
      maxResults: Option[Int] = None
  ): Either[SqsClientError, List[MessageMoveTask]]
  def cancelMessageMoveTask(taskHandle: TaskHandle): Either[SqsClientError, ApproximateNumberOfMessagesMoved]

  def addPermission(queueUrl: QueueUrl, label: String, awsAccountIds: List[String], actions: List[String]): Unit
  def removePermission(queueUrl: QueueUrl, label: String): Unit

  def listDeadLetterSourceQueues(queueUrl: QueueUrl): List[QueueUrl]
}
