package org.elastimq.replication

import org.elasticmq.{QueueOperations, Queue, MessageOperations, Message}
import org.elasticmq.delegate.Wrapper

class ReplicationWrapperModule {
  this: ReplicatingQueueModule with ReplicatingMessageModule =>

  private class ReplicationWrapper extends Wrapper {
    def wrapQueueOperations(queueOperations: QueueOperations) = replicatingQueueOperations(queueOperations)

    def wrapQueue(queue: Queue) = replicatingQueue(queue)

    def wrapMessageOperations(messageOperations: MessageOperations) = replicatingMessageOperations(messageOperations)

    def wrapMessage(message: Message) = replicatingMessage(message)
  }

  val replicationWrapper: Wrapper = new ReplicationWrapper
}
