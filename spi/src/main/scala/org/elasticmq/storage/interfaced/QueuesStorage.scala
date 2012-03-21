package org.elasticmq.storage.interfaced

import org.elasticmq.QueueStatistics
import org.elasticmq.data.QueueData

trait QueuesStorage {
  def createQueue(queue: QueueData)
  def updateQueue(queue: QueueData)
  def deleteQueue(name: String)
  def lookupQueue(name: String): Option[QueueData]
  def listQueues: Seq[QueueData]
  def queueStatistics(name: String, deliveryTime: Long): QueueStatistics
  def clear()
}