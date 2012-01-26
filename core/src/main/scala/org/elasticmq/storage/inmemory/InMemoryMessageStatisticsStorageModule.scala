package org.elasticmq.storage.inmemory

import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq._

trait InMemoryMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: InMemoryMessageStorageRegistryModule =>
  
  class InMemoryMessageStatisticsStorage(queueName: String) extends MessageStatisticsStorage {
    // TODO: make nicer

    def readMessageStatistics(messageId: MessageId) = storageRegistry.messageStats.get(messageId.id)
      .getOrElse(MessageStatistics(NeverReceived, 0))

    def writeMessageStatistics(messageId: MessageId, messageStatistics: MessageStatistics) {
      // TODO: checking if the message isn't deleted.
      if (storageRegistry.getStoreForQueue(queueName).messagesById.get(messageId.id) != None) {
        storageRegistry.messageStats.put(messageId.id, messageStatistics)
      }
    }
    
    def removeMessageStatistics(messageId: MessageId) = storageRegistry.messageStats.remove(messageId.id)
  }

  def messageStatisticsStorage(queueName: String) = new InMemoryMessageStatisticsStorage(queueName)
}