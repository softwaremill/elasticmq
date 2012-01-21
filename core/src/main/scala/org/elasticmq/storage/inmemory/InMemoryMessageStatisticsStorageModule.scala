package org.elasticmq.storage.inmemory

import org.elasticmq.storage.MessageStatisticsStorageModule
import collection.JavaConversions
import java.util.concurrent.ConcurrentHashMap
import org.elasticmq._

trait InMemoryMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: InMemoryMessageStorageModule => // TODO: remove the circular dependency
  
  class InMemoryMessageStatisticsStorage extends MessageStatisticsStorage {
    val messageStats = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, MessageStatistics])

    def readMessageStatistics(message: SpecifiedMessage) = messageStats.get(message.id.get)
      .getOrElse(MessageStatistics(message, NeverReceived, 0))

    def writeMessageStatistics(messageStatistics: MessageStatistics) {
      // TODO: checking if the message isn't deleted.
      if (messageStorage.getStoreForQueue(messageStatistics.message.queue.name).messagesById
        .get(messageStatistics.message.id.get) != None) {
        messageStats.put(messageStatistics.message.id.get, messageStatistics)
      }
    }
    
    def removeMessageStatistics(message: IdentifiableMessage) = messageStats.remove(message.id.get)
  }

  val messageStatisticsStorage = new InMemoryMessageStatisticsStorage
}