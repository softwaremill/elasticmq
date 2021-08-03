package org.elasticmq.server

import akka.actor.Actor
import org.elasticmq.QueueData
import org.elasticmq.actor.queue.{PersistQueue, RemoveQueue, UpdateQueueMetadata}

import scala.collection.mutable

class QueueConfigStore(storagePath: String) extends Actor {

  private val queues: mutable.Map[String, QueueData] = mutable.HashMap[String, QueueData]()

  private val queuePersister: QueuePersister = QueuePersister(storagePath)

  def receive: Receive = {
    case PersistQueue(queue) =>
      queues.put(queue.name, queue)
      queuePersister.saveToConfigFile(queues.values.toList)
    case RemoveQueue(queueName) =>
      queues.remove(queueName)
      queuePersister.saveToConfigFile(queues.values.toList)
    case UpdateQueueMetadata(queue) =>
      queues.put(queue.name, queue)
      queuePersister.saveToConfigFile(queues.values.toList)
  }
}
