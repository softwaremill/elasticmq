package org.elasticmq.data

import org.elasticmq.{MessageId, MessageStatistics}

trait DataSource {
  def queuesData: TraversableOnce[QueueData]
  def messagesData(queue: QueueData): TraversableOnce[MessageData]
  def messageStatisticsWithId(queue: QueueData): TraversableOnce[(MessageId, MessageStatistics)]
}
