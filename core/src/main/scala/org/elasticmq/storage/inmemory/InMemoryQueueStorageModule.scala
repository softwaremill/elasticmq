package org.elasticmq.storage.inmemory

import org.elasticmq.storage.{MessageStatisticsStorageModule, QueueStorageModule}
import org.elasticmq.data.QueueData
import org.elasticmq.{MessageId, QueueStatistics}

trait InMemoryQueueStorageModule extends QueueStorageModule {
  this: InMemoryStorageModelModule with InMemoryStorageRegistryModule with MessageStatisticsStorageModule =>

  class InMemoryQueueStorage extends QueueStorage {
    def persistQueue(queue: QueueData) {
      storageRegistry.createStoreForQueue(queue)
    }

    def updateQueue(queueData: QueueData) {
      storageRegistry.updateQueueData(queueData)
    }

    def deleteQueue(queueName: String) {
      storageRegistry.deleteStoreForQueue(queueName)
    }

    def lookupQueue(queueName: String) = storageRegistry.storages.get(queueName).map(_.queueData)

    def listQueues = storageRegistry.storages.values.map(_.queueData).toSeq

    def queueStatistics(queueName: String, deliveryTime: Long) = {
      val queueMessageStorage = storageRegistry.getStoreForQueue(queueName).messageStorage
      val (visible, invisible) = queueMessageStorage.messagesById.values.partition(
        message => (message.nextDelivery.get() <= deliveryTime))

      val (delivered, undelivered) = invisible.partition(
        message => messageStatisticsStorage(queueName).readMessageStatistics(MessageId(message.id)).approximateReceiveCount > 0)

      QueueStatistics(
        visible.size,
        delivered.size,
        undelivered.size)
    }
  }

  val queueStorage = new InMemoryQueueStorage
}