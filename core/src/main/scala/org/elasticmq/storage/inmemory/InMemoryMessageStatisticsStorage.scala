package org.elasticmq.storage.inmemory

import org.elasticmq.{MessageDoesNotExistException, MessageStatistics, MessageId}

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConversions._
import scala.collection.mutable.ConcurrentMap
import org.elasticmq.storage.interfaced.MessageStatisticsStorage

class InMemoryMessageStatisticsStorage(queueName: String)
  // Serializable because of state management (see InMemoryStorageState)
  extends MessageStatisticsStorage with Serializable {

  val statistics: ConcurrentMap[MessageId, MessageStatistics] = new ConcurrentHashMap[MessageId, MessageStatistics]

  def readMessageStatistics(messageId: MessageId) =
    statistics.get(messageId).getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

  def updateMessageStatistics(messageId: MessageId, messageStatistics: MessageStatistics) {
    val previousOption = statistics.put(messageId, messageStatistics)

    if (messageStatistics.approximateReceiveCount != 0) {
      // Not an initial write, previous value should be defined. If not, the message got deleted, cleaning up.
      if (!previousOption.isDefined) {
        deleteMessageStatistics(messageId);
      }
    }
  }

  def deleteMessageStatistics(messageId: MessageId) = statistics.remove(messageId)
}
