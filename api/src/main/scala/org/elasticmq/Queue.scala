package org.elasticmq

import org.joda.time.{Duration, DateTime}

case class Queue(name: String,
                 defaultVisibilityTimeout: MillisVisibilityTimeout,
                 delay: Duration,
                 created: DateTime,
                 lastModified: DateTime)

object Queue {
  def apply(name: String, defaultVisibilityTimeout: MillisVisibilityTimeout): Queue = {
    Queue(name, defaultVisibilityTimeout, Duration.ZERO, UnspecifiedDate, UnspecifiedDate)
  }
}