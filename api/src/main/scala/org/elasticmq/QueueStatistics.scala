package org.elasticmq

case class QueueStatistics(queue: Queue,
                           approximateNumberOfVisibleMessages: Long,
                           approximateNumberOfInvisibleMessages: Long,
                           approximateNumberOfMessagesDelayed: Long)