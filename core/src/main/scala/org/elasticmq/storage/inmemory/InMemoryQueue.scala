package org.elasticmq.storage.inmemory

import org.elasticmq.data.QueueData

case class InMemoryQueue(queueData: QueueData,
                         messages: InMemoryMessageStorage,
                         statistics: InMemoryMessageStatisticsStorage)

