package org.elasticmq.storage

import org.elasticmq.{QueueStatistics, Queue}

trait QueueStorageModule {
  trait QueueStorage {
    def persistQueue(queue: Queue)
    def updateQueue(queue: Queue)
    def deleteQueue(queue: Queue)
    def lookupQueue(name: String): Option[Queue]
    def listQueues: Seq[Queue]
    def queueStatistics(queue: Queue, deliveryTime: Long): QueueStatistics
  }

  def queueStorage: QueueStorage
}