package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class QueueData(name: String,
  defaultVisibilityTimeout: MillisVisibilityTimeout,
  delay: Duration,
  receiveMessageWait: Duration,
  created: DateTime,
  lastModified: DateTime,
  deadLettersQueue: Option[DeadLettersQueueData] = None,
  maxReceiveCount: Option[Int] = None
)

class DeadLettersQueueData(val name: String, val maxReceiveCount: Int)

object DeadLettersQueueData {
  def apply(name: String, maxReceiveCount: Int): DeadLettersQueueData = {
    val mrc = math.min(math.max(maxReceiveCount, 1), 1000)
    new DeadLettersQueueData(name, mrc)
  }
}
