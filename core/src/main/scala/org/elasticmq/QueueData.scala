package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(name: String,
  defaultVisibilityTimeout: MillisVisibilityTimeout,
  delay: Duration,
  receiveMessageWait: Duration,
  created: DateTime,
  lastModified: DateTime,
  deadLettersQueue: Option[QueueData] = None,
  maxReceiveCount: Option[Int] = None,
  isDeadLettersQueue: Boolean = false
) {
  def adjustLimits(): QueueData = {
    this.copy(maxReceiveCount = maxReceiveCount.map(mrc => math.min(math.max(mrc, 1), 1000)))
  }
}
