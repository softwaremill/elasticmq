package org.elasticmq

import java.time.{Duration, OffsetDateTime}

case class QueueData(
    name: String,
    defaultVisibilityTimeout: MillisVisibilityTimeout,
    delay: Duration,
    receiveMessageWait: Duration,
    created: OffsetDateTime,
    lastModified: OffsetDateTime,
    deadLettersQueue: Option[DeadLettersQueueData] = None,
    isFifo: Boolean = false,
    hasContentBasedDeduplication: Boolean = false,
    copyMessagesTo: Option[String] = None,
    moveMessagesTo: Option[String] = None,
    tags: Map[String, String] = Map[String, String]()
)

case class DeadLettersQueueData(name: String, maxReceiveCount: Int)
