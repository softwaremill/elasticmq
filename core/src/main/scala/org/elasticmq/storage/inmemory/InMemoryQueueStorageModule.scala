package org.elasticmq.storage.inmemory

import collection.JavaConversions
import java.util.concurrent.ConcurrentHashMap
import org.elasticmq.{QueueStatistics, Queue}
import org.elasticmq.storage.{MessageStatisticsStorageModule, QueueStorageModule}

trait InMemoryQueueStorageModule extends QueueStorageModule {
  this: InMemoryStorageModelModule with InMemoryMessageStorageModule with MessageStatisticsStorageModule =>

  class InMemoryQueueStorage extends QueueStorage {
    val queuesByName = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, Queue])

    def persistQueue(queue: Queue) {
      // First creating the storage, so that when the queue is findable (present in queuesByName), the storage exists.
      messageStorage.createStorForQueue(queue.name)
      updateQueue(queue)
    }

    def updateQueue(queue: Queue) { queuesByName.put(queue.name, queue) }

    def deleteQueue(queue: Queue) {
      queuesByName.remove(queue.name)
      messageStorage.deleteStoreForQueue(queue.name)
    }

    def lookupQueue(name: String) = queuesByName.get(name)

    def listQueues = queuesByName.values.toSeq

    def queueStatistics(queue: Queue, deliveryTime: Long) = {
      val queueMessageStorage = messageStorage.getStoreForQueue(queue.name)
      val (visible, invisible) = queueMessageStorage.messagesById.values.partition(
        message => (message.nextDelivery.get() <= deliveryTime))

      val (delivered, undelivered) = invisible.partition(
        message => messageStatisticsStorage.readMessageStatistics(message.toMessage(queue)).approximateReceiveCount > 0)

      QueueStatistics(queue,
        visible.size,
        delivered.size,
        undelivered.size)
    }
  }

  val queueStorage = new InMemoryQueueStorage
}