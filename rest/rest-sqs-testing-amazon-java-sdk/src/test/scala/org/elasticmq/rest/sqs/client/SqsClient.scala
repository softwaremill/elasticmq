package org.elasticmq.rest.sqs.client
import org.elasticmq.MessageAttribute

trait SqsClient {

  def createQueue(queueName: String, attributes: Map[QueueAttributeName, String] = Map.empty): QueueUrl
  def getQueueUrl(queueName: String): Either[SqsClientError, QueueUrl]
  def getQueueAttributes(queueUrl: QueueUrl, attributeNames: QueueAttributeName*): Map[String, String]

  def sendMessage(
      queueUrl: QueueUrl,
      messageBody: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty
  ): Unit
  def receiveMessage(queueUrl: QueueUrl, systemAttributes: List[String] = List.empty, messageAttributes: List[String] = List.empty): List[ReceivedMessage]

  def startMessageMoveTask(
      sourceArn: Arn,
      maxNumberOfMessagesPerSecond: Option[Int] = None
  ): Either[SqsClientError, TaskHandle]
  def listMessageMoveTasks(
      sourceArn: Arn,
      maxResults: Option[Int] = None
  ): Either[SqsClientError, List[MessageMoveTask]]
  def cancelMessageMoveTask(taskHandle: TaskHandle): Either[SqsClientError, ApproximateNumberOfMessagesMoved]
}
