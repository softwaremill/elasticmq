package org.elasticmq.delegate

import org.elasticmq.{QueueBuilder, Client}

trait ClientDelegate extends Client {
  val delegate: Client
  val wrapper: Wrapper = IdentityWrapper
  
  def createQueue(queueBuilder: QueueBuilder) = wrapper.wrapQueue(delegate.createQueue(queueBuilder))

  def createQueue(name: String) = wrapper.wrapQueue(delegate.createQueue(name))

  def lookupQueue(name: String) = delegate.lookupQueue(name).map(wrapper.wrapQueue(_))

  def lookupOrCreateQueue(queueBuilder: QueueBuilder) = wrapper.wrapQueue(delegate.lookupOrCreateQueue(queueBuilder))

  def lookupOrCreateQueue(name: String) = wrapper.wrapQueue(delegate.lookupOrCreateQueue(name))

  def listQueues = delegate.listQueues.map(wrapper.wrapQueue(_))

  def queueOperations(name: String) = wrapper.wrapQueueOperations(delegate.queueOperations(name))
}
