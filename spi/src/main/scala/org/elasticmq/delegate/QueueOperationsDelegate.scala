package org.elasticmq.delegate

import org.joda.time.Duration
import org.elasticmq._

trait QueueOperationsDelegate extends QueueOperations {
  val delegate: QueueOperations
  val wrapper: Wrapper = IdentityWrapper

  def sendMessage(content: String) = wrapper.wrapMessage(delegate.sendMessage(content))

  def sendMessage(messageBuilder: MessageBuilder) = wrapper.wrapMessage(delegate.sendMessage(messageBuilder))

  def receiveMessage() = delegate.receiveMessage().map(wrapper.wrapMessage(_))

  def receiveMessage(visibilityTimeout: VisibilityTimeout) = delegate.receiveMessage(visibilityTimeout).map(wrapper.wrapMessage(_))

  def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout) =
    delegate.receiveMessageWithStatistics(visibilityTimeout).map { case (msg, stats) => (wrapper.wrapMessage(msg), stats) }

  def lookupMessage(id: MessageId) = delegate.lookupMessage(id).map(wrapper.wrapMessage(_))

  def updateDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout) =
    wrapper.wrapQueue(delegate.updateDefaultVisibilityTimeout(defaultVisibilityTimeout))

  def updateDelay(delay: Duration) = wrapper.wrapQueue(delegate.updateDelay(delay))

  def fetchStatistics() = delegate.fetchStatistics()

  def delete() { delegate.delete() }

  def fetchQueue() = wrapper.wrapQueue(delegate.fetchQueue())

  def messageOperations(id: MessageId) = wrapper.wrapMessageOperations(delegate.messageOperations(id))
}
