package org.elasticmq.storage.inmemory

import collection.JavaConversions
import java.util.concurrent.ConcurrentHashMap
import org.elasticmq.storage.{MessageStatisticsStorageModule, QueueStorageModule}
import org.elasticmq.impl.QueueData
import org.elasticmq.{MessageId, QueueStatistics}

trait InMemoryQueueStorageModule extends QueueStorageModule {
  this: InMemoryStorageModelModule with InMemoryMessageStorageRegistryModule with MessageStatisticsStorageModule =>

  class InMemoryQueueStorage extends QueueStorage {
    val queuesByName = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, QueueData])

    def persistQueue(queue: QueueData) {
      // First creating the storage, so that when the queue is findable (present in queuesByName), the storage exists.
      storageRegistry.createStoreForQueue(queue.name)
      updateQueue(queue)
    }

    def updateQueue(queue: QueueData) { queuesByName.put(queue.name, queue) }

    def deleteQueue(queueName: String) {
      queuesByName.remove(queueName)
      storageRegistry.deleteStoreForQueue(queueName)
    }

    def lookupQueue(queueName: String) = queuesByName.get(queueName)

    def listQueues = queuesByName.values.toSeq

    def queueStatistics(queueName: String, deliveryTime: Long) = {
      val queueMessageStorage = storageRegistry.getStoreForQueue(queueName)
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