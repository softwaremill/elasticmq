package org.elasticmq.storage.inmemory

import org.elasticmq.data.{DataSource, QueueData}

class InMemoryDataSource(inMemoryQueuesStorage: InMemoryQueuesStorage) extends DataSource {
  def queuesData = {
    inMemoryQueuesStorage.queues.values.map(_.queueData)
  }

  def messagesData(queue: QueueData) = {
    inMemoryQueuesStorage(queue.name).messages.messagesById.values.map(_.toMessageData)
  }

  def messageStatisticsWithId(queue: QueueData) = {
    inMemoryQueuesStorage(queue.name).statistics.statistics
  }
}
