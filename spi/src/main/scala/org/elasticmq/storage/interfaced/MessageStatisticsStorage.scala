package org.elasticmq.storage.interfaced

import org.elasticmq._

trait MessageStatisticsStorage {
  def updateMessageStatistics(messageId: MessageId, messageStatistics: MessageStatistics)
  def readMessageStatistics(messageId: MessageId): MessageStatistics
}