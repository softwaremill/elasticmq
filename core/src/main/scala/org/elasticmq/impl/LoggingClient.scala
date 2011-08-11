package org.elasticmq.impl

import org.apache.log4j.Logger
import org.elasticmq._

trait LoggingClient extends Client {
  abstract override def queueClient = new QueueClientLoggingWrapper(super.queueClient)
  abstract override def messageClient = new MessageClientLogginWrapper(super.messageClient)
}

class QueueClientLoggingWrapper(delegate: QueueClient) extends QueueClient {
  val log = Logger.getLogger(this.getClass)

  def createQueue(queue: Queue) = {
    log.debug("Creating queue: "+queue);
    delegate.createQueue(queue)
  }

  def lookupQueue(name: String) = {
    log.debug("Looking up queue: "+name);
    delegate.lookupQueue(name)
  }

  def updateDefaultVisibilityTimeout(queue: Queue, newDefaultVisibilityTimeout: MillisVisibilityTimeout) = {
    log.debug("Updating default visibility timeout: "+queue+" with: "+newDefaultVisibilityTimeout);
    delegate.updateDefaultVisibilityTimeout(queue, newDefaultVisibilityTimeout)
  }

  def deleteQueue(queue: Queue) {
    log.debug("Deleting queue: " + queue)
    delegate.deleteQueue(queue);
  }

  def listQueues = {
    log.debug("Listing queues")
    delegate.listQueues
  }
}

class MessageClientLogginWrapper(delegate: MessageClient) extends MessageClient {
  val log = Logger.getLogger(this.getClass)

  def sendMessage(message: Message) = {
    log.debug("Sending message: "+message)
    delegate.sendMessage(message)
  }

  def receiveMessage(queue: Queue) = {
    log.debug("Receiving message: "+queue)
    delegate.receiveMessage(queue)
  }

  def updateVisibilityTimeout(message: Message, newVisibilityTimeout: MillisVisibilityTimeout) = {
    log.debug("Updating visibility timeout for: "+message+" with: "+newVisibilityTimeout)
    delegate.updateVisibilityTimeout(message, newVisibilityTimeout)
  }

  def deleteMessage(message: Message) {
    log.debug("Deleting message: "+message)
    delegate.deleteMessage(message)
  }

  def lookupMessage(id: String) = {
    log.debug("Looking up message: "+id)
    delegate.lookupMessage(id)
  }
}