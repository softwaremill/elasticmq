package org.elasticmq.storage.inmemory

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions
import org.elasticmq.{QueueAlreadyExistsException, MessageStatistics, QueueDoesNotExistException}
import org.elasticmq.impl.QueueData

trait InMemoryStorageRegistryModule {
  this: InMemoryStorageModelModule =>

  class InMemoryStorageRegistry {
    val storages = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, InMemoryQueue])

    def createStoreForQueue(queueData: QueueData) {
      if (storages.putIfAbsent(queueData.name, InMemoryQueue(queueData)) != None) {
        throw new QueueAlreadyExistsException(queueData.name)
      }
    }
    
    def updateQueueData(queueData: QueueData) {
      val queueName = queueData.name
      storages.put(queueName,
        getStoreForQueue(queueName).copy(queueData = queueData))
    }

    def deleteStoreForQueue(queueName: String) {
      storages.remove(queueName)
    }

    def getStoreForQueue(queueName: String): InMemoryQueue = {
      storages.get(queueName).getOrElse(throw new QueueDoesNotExistException(queueName))
    }
  }

  val storageRegistry = new InMemoryStorageRegistry
}