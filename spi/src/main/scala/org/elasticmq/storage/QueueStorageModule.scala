package org.elasticmq.storage

import org.elasticmq.QueueStatistics
import org.elasticmq.data.QueueData

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