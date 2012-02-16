package org.elastimq.replication

import org.elasticmq.delegate.{MessageDelegate, MessageOperationsDelegate}
import org.elasticmq.{Message, MillisVisibilityTimeout, MessageOperations}

class ReplicatingMessageModule {
  this: ReplicationWrapperModule =>
  
  private class ReplicatingMessageOperations(val delegate: MessageOperations) extends MessageOperationsDelegate {
    override val wrapper = replicationWrapper

    override def updateVisibilityTimeout(newVisibilityTimeout: MillisVisibilityTimeout) = null

    override def delete() {}
  }
  
  private class ReplicatingMessage(override val delegate: Message)
    extends ReplicatingMessageOperations(delegate) with MessageDelegate

  def replicatingMessageOperations(delegate: MessageOperations): MessageOperations =
    new ReplicatingMessageOperations(delegate: MessageOperations)

  def replicatingMessage(delegate: Message): Message = new ReplicatingMessage(delegate: Message)
}
