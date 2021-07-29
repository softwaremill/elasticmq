package org.elasticmq.actor.queue

import org.elasticmq.QueueData

trait QueuePersister {

  def persist(queue: QueueData): Unit
  def remove(queueName: String): Unit
  def update(queue: QueueData): Unit

}
