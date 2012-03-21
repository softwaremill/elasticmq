package org.elasticmq.storage.inmemory

import org.elasticmq.storage.interfaced.InterfacedCommandExecutor
import org.elasticmq.data.DataSource

class InMemoryStorageCommandExecutor extends InterfacedCommandExecutor {
  val queues = new InMemoryQueuesStorage(queueData => {
    val statistics = new InMemoryMessageStatisticsStorage(queueData.name)
    new InMemoryQueue(
      queueData,
      new InMemoryMessagesStorage(queueData.name, statistics),
      statistics)
  })

  def queuesStorage = queues
  def messagesStorage(queueName: String) = queues(queueName).messages
  def messageStatisticsStorage(queueName: String) = queues(queueName).statistics

  def executeStateManagement[T](f: (DataSource) => T) = f(new InMemoryDataSource(queues))
}
