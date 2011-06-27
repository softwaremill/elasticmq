package org.elasticmq

trait Client {
  def createQueue(queue: Queue): Queue
  def deleteQueue(queue: Queue)

  def sendMessage(message: Message): Message
  def receveiveMessage(queue: Queue): Option[Message]
}

