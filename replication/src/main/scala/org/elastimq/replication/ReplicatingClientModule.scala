package org.elastimq.replication

import org.elasticmq.delegate.ClientDelegate
import org.elasticmq.{QueueBuilder, Client}

class ReplicatingClientModule {
  this: ReplicationWrapperModule =>

  private class ReplicatingClient(val delegate: Client) extends ClientDelegate {
    override val wrapper = replicationWrapper

    override def createQueue(queueBuilder: QueueBuilder) = null

    override def createQueue(name: String) = null
  }

  def replicatingClient(delegate: Client): Client = new ReplicatingClient(delegate)
}
