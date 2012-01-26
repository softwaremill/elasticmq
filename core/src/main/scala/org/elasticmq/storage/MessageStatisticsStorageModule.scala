package org.elasticmq.storage

import org.elasticmq._

trait MessageStatisticsStorageModule {
  trait MessageStatisticsStorage {
    def writeMessageStatistics(messageId: MessageId, messageStatistics: MessageStatistics)
    def readMessageStatistics(messageId: MessageId): MessageStatistics
  }

  def messageStatisticsStorage(queueName: String): MessageStatisticsStorage
}