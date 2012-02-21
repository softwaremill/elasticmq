package org.elasticmq.storage.inmemory

import org.elasticmq.data.QueueData

case class InMemoryQueue(queueData: QueueData,
                         messages: InMemoryMessageStorage,
                         statistics: InMemoryMessageStatisticsStorage)

object InMemoryQueue {
  def apply(queueData: QueueData) = {
    val statistics = new InMemoryMessageStatisticsStorage(queueData.name)
    new InMemoryQueue(queueData,
      new InMemoryMessageStorage(queueData.name, statistics),
      statistics)
  }
}
