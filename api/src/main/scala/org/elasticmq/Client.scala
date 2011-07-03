package org.elasticmq

trait Client {
  def queueClient: QueueClient
  def messageClient: MessageClient
}

trait QueueClient {
  def createQueue(queue: Queue): Queue
  def lookupQueue(name: String): Option[Queue]
  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: MillisVisibilityTimeout): Queue
  def deleteQueue(queue: Queue)
}

trait MessageClient {
  def sendMessage(message: Message): Message
  def receiveMessage(queue: Queue): Option[Message]
  def updateVisibilityTimeout(message: Message, newVisibilityTimeout: MillisVisibilityTimeout): Message
  def deleteMessage(message: Message)
}

