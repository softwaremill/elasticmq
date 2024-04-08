package org.elasticmq.rest.sqs.client

trait SqsClient {

  def createQueue(queueName: String, attributes: Map[QueueAttributeName, String] = Map.empty): QueueUrl
  def sendMessage(queueUrl: QueueUrl, messageBody: String): Unit
  def receiveMessage(queueUrl: QueueUrl): List[ReceivedMessage]
  def getQueueAttributes(queueUrl: QueueUrl, attributeNames: QueueAttributeName*): Map[String, String]
  def startMessageMoveTask(sourceArn: Arn, maxNumberOfMessagesPerSecond: Option[Int] = None): Either[SqsClientError, TaskHandle]
  def listMessageMoveTasks(sourceArn: Arn, maxResults: Option[Int] = None): Either[SqsClientError, List[MessageMoveTask]]
  def cancelMessageMoveTask(taskHandle: TaskHandle): Either[SqsClientError, ApproximateNumberOfMessagesMoved]
}
