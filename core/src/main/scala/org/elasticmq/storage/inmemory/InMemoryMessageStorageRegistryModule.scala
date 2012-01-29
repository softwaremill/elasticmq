package org.elasticmq.storage.inmemory

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions
import org.elasticmq.{QueueAlreadyExistsException, MessageStatistics, QueueDoesNotExistException}

trait InMemoryMessageStorageRegistryModule {
  this: InMemoryMessageStorageModule =>

  class InMemoryMessageStorageRegistry {
    private val messageStores = JavaConversions.asScalaConcurrentMap(
      new ConcurrentHashMap[String, OneQueueInMemoryMessageStorage])

    val messageStats = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, MessageStatistics])

    def createStoreForQueue(queueName: String) {
      if (messageStores.putIfAbsent(queueName, new OneQueueInMemoryMessageStorage(queueName)) != None) {
        throw new QueueAlreadyExistsException(queueName)
      }
    }

    def deleteStoreForQueue(queueName: String) {
      messageStores.remove(queueName)
    }

    def getStoreForQueue(queueName: String): OneQueueInMemoryMessageStorage = {
      messageStores.get(queueName).getOrElse(throw new QueueDoesNotExistException(queueName))
    }
  }

  val storageRegistry = new InMemoryMessageStorageRegistry
}