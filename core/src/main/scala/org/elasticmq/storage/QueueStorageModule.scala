package org.elasticmq.storage

import org.elasticmq.{QueueStatistics, Queue}
import org.elasticmq.impl.QueueData

trait QueueStorageModule {
  trait QueueStorage {
    def persistQueue(queue: QueueData)
    def updateQueue(queue: QueueData)
    def deleteQueue(name: String)
    def lookupQueue(name: String): Option[QueueData]
    def listQueues: Seq[QueueData]
    def queueStatistics(name: String, deliveryTime: Long): QueueStatistics
  }

  def queueStorage: QueueStorage
}