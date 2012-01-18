package org.elasticmq

import org.joda.time.Duration

trait Client {
  def queueClient: QueueClient
  def messageClient: MessageClient
}

trait QueueClient {
  def createQueue(queue: Queue): Queue
  def lookupQueue(name: String): Option[Queue]
  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: MillisVisibilityTimeout): Queue
  def updateDelay(queue: Queue, newDelay: Duration): Queue
  def deleteQueue(queue: Queue)
  def listQueues: Seq[Queue]
  def queueStatistics(queue: Queue): QueueStatistics
}

trait MessageClient {
  def sendMessage(message: AnyMessage): SpecifiedMessage
  def receiveMessage(queue: Queue, visibilityTimeout: VisibilityTimeout): Option[SpecifiedMessage]
  def receiveMessageWithStatistics(queue: Queue, visibilityTimeout: VisibilityTimeout): Option[MessageStatistics]
  def updateVisibilityTimeout(message: SpecifiedMessage, newVisibilityTimeout: MillisVisibilityTimeout): SpecifiedMessage
  def deleteMessage(message: IdentifiableMessage)
  def lookupMessage(queue: Queue, id: String): Option[SpecifiedMessage]
  def messageStatistics(message: SpecifiedMessage): MessageStatistics
}

