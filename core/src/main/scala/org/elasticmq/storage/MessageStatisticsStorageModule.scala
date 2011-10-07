package org.elasticmq.storage

import org.elasticmq._

trait MessageStatisticsStorageModule {
  trait MessageStatisticsStorage {
    def writeMessageStatistics(messageStatistics: MessageStatistics)
    def readMessageStatistics(message: SpecifiedMessage): MessageStatistics
  }

  def messageStatisticsStorage: MessageStatisticsStorage
}