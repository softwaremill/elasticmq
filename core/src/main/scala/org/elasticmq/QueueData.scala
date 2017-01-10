package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(name: String,
  defaultVisibilityTimeout: MillisVisibilityTimeout,
  delay: Duration,
  receiveMessageWait: Duration,
  deadLettersQueue: Option[QueueData],
  maxReceiveCount: Int,
  created: DateTime,
  lastModified: DateTime,
  isDeadLettersQueue: Boolean = false)