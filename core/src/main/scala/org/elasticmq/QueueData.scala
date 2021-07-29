package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(
    name: String,
    defaultVisibilityTimeout: MillisVisibilityTimeout,
    delay: Duration,
    receiveMessageWait: Duration,
    created: DateTime,
    lastModified: DateTime,
    deadLettersQueue: Option[DeadLettersQueueData] = None,
    isFifo: Boolean = false,
    hasContentBasedDeduplication: Boolean = false,
    copyMessagesTo: Option[String] = None,
    moveMessagesTo: Option[String] = None,
    tags: Map[String, String] = Map[String, String]()
)

case class QueueMetadata(
    defaultVisibilityTimeout: Long,
    delay: Long,
    receiveMessageWait: Long,
    deadLettersQueue: Option[DeadLettersQueueData],
    isFifo: Boolean,
    hasContentBasedDeduplication: Boolean,
    copyMessagesTo: String,
    moveMessagesTo: String,
    tags: Map[String, String]
)

case class DeadLettersQueueData(name: String, maxReceiveCount: Int)
