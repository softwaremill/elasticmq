package org.elasticmq.delegate

import org.elasticmq.{MillisVisibilityTimeout, MessageOperations}

trait MessageOperationsDelegate extends MessageOperations {
  val delegate: MessageOperations
  val wrapper: Wrapper = IdentityWrapper
  
  def updateVisibilityTimeout(newVisibilityTimeout: MillisVisibilityTimeout) = 
    wrapper.wrapMessage(delegate.updateVisibilityTimeout(newVisibilityTimeout))

  def fetchStatistics() = delegate.fetchStatistics()

  def delete() { delegate.delete() }

  def fetchMessage() = wrapper.wrapMessage(delegate.fetchMessage())
}
