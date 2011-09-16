package org.elasticmq

trait Client {
  def queueClient: QueueClient
  def messageClient: MessageClient
}

trait QueueClient {
  def createQueue(queue: Queue): Queue
  def lookupQueue(name: String): Option[Queue]
  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: VisibilityTimeout): Queue
  def deleteQueue(queue: Queue)
  def listQueues: Seq[Queue]
}

trait MessageClient {
  def sendMessage(message: AnyMessage): SpecifiedMessage
  def receiveMessage(queue: Queue): Option[SpecifiedMessage]
  def updateVisibilityTimeout(message: IdentifiableMessage, newVisibilityTimeout: VisibilityTimeout): SpecifiedMessage
  def deleteMessage(message: IdentifiableMessage)
  def lookupMessage(id: String): Option[SpecifiedMessage]
}

