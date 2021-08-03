package org.elasticmq.server

import akka.actor.Actor
import org.elasticmq.QueueData
import org.elasticmq.actor.queue.{QueueCreated, QueueDeleted, QueueMetadataUpdated}

import scala.collection.mutable

class QueueConfigStore(storagePath: String) extends Actor {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  def receive: Receive = {
    case QueueCreated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)
    case QueueDeleted(queueName) =>
      queues.remove(queueName)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)
    case QueueMetadataUpdated(queue) =>
      queues.put(queue.name, queue)
      QueuePersister.saveToConfigFile(queues.values.toList, storagePath)
  }
}
