package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(name: String,
  defaultVisibilityTimeout: MillisVisibilityTimeout,
  delay: Duration,
  receiveMessageWait: Duration,
  created: DateTime,
  lastModified: DateTime,
  deadLettersQueue: Option[DeadLettersQueueData] = None,
  maxReceiveCount: Option[Int] = None,
  isFifo: Boolean = false,
  hasContentBasedDeduplication: Boolean = false
)

case class DeadLettersQueueData(name: String, maxReceiveCount: Int)
