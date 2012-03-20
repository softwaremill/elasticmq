package org.elasticmq.storage.statemanager.command

import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.{MessageId, MessageStatistics}

trait MQDataSource {
  def queuesData: TraversableOnce[QueueData]
  def messagesData(queue: QueueData): TraversableOnce[MessageData]
  def messageStatisticsWithId(queue: QueueData): TraversableOnce[(MessageId, MessageStatistics)]
}
