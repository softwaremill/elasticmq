package org.elasticmq.storage.inmemory

import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions
import scala.collection.mutable.ConcurrentMap

trait InMemoryMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: InMemoryStorageRegistryModule with InMemoryStorageModelModule =>
  
  class InMemoryMessageStatisticsStorage(queueName: String) extends MessageStatisticsStorage {
    val storage: ConcurrentMap[MessageId, MessageStatistics] =
      JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[MessageId, MessageStatistics])

    def readMessageStatistics(messageId: MessageId) =
      storage.get(messageId).getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

    def writeMessageStatistics(messageId: MessageId, messageStatistics: MessageStatistics) {
      val previousOption = storage.put(messageId, messageStatistics)
      
      if (messageStatistics.approximateReceiveCount != 0) {
        // Not an initial write, previous value should be defined. If not, the message got deleted, cleaning up.
        if (!previousOption.isDefined) {
          removeMessageStatistics(messageId);
        }
      }
    }
    
    def removeMessageStatistics(messageId: MessageId) = storage.remove(messageId)
  }

  def messageStatisticsStorage(queueName: String) = storageRegistry.getStoreForQueue(queueName).statisticStorage
}