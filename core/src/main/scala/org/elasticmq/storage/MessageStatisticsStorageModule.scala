package org.elasticmq.storage

import org.elasticmq._

trait MessageStatisticsStorageModule {
  trait MessageStatisticsStorage {
    def messageReceived(message: IdentifiableMessage, when: Long)
    def readMessageStatistics(message: IdentifiableMessage): MessageStatistics
  }

  def messageStatisticsStorage: MessageStatisticsStorage
}