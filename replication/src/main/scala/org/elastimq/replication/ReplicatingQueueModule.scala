package org.elastimq.replication

import org.elasticmq.delegate.{QueueOperationsDelegate, QueueDelegate}
import org.joda.time.Duration
import org.elasticmq._

class ReplicatingQueueModule {
  this: ReplicationWrapperModule =>

  private class ReplicatingQueueOperations(val delegate: QueueOperations) extends QueueOperationsDelegate {
    override val wrapper = replicationWrapper

    override def sendMessage(content: String) = null

    override def sendMessage(messageBuilder: MessageBuilder) = null

    override def receiveMessage() = null

    override def receiveMessage(visibilityTimeout: VisibilityTimeout) = null

    override def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout) = null

    override def updateDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout) = null

    override def updateDelay(delay: Duration) = null

    override def delete() {}
  }

  private class ReplicatingQueue(override val delegate: Queue)
    extends ReplicatingQueueOperations(delegate) with QueueDelegate

  def replicatingQueueOperations(delegate: QueueOperations): QueueOperations = new ReplicatingQueueOperations(delegate)

  def replicatingQueue(delegate: Queue): Queue = new ReplicatingQueue(delegate: Queue)
}
