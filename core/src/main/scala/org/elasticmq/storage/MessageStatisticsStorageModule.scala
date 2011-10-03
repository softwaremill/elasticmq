package org.elasticmq.storage

import org.elasticmq._

trait MessageStatisticsStorageModule {
  trait MessageStatisticsStorage {
    def messageReceived(message: IdentifiableMessage, when: Long)
    def messageStatistics(message: IdentifiableMessage): MessageStatistics
  }

  def messageStatisticsStorage: MessageStatisticsStorage
}