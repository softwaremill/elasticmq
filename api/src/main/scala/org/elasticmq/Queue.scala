package org.elasticmq

import org.joda.time.DateTime

case class Queue(name: String,
                 defaultVisibilityTimeout: MillisVisibilityTimeout,
                 created: DateTime,
                 lastModified: DateTime)

object Queue {
  def apply(name: String, defaultVisibilityTimeout: MillisVisibilityTimeout): Queue = {
    Queue(name, defaultVisibilityTimeout, UnspecifiedDate, UnspecifiedDate)
  }
}