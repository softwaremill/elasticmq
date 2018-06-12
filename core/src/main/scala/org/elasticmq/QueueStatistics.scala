package org.elasticmq

case class QueueStatistics(approximateNumberOfVisibleMessages: Long,
                           approximateNumberOfInvisibleMessages: Long,
                           approximateNumberOfMessagesDelayed: Long)
