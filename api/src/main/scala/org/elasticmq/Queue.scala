package org.elasticmq

import org.joda.time.DateTime

case class Queue(name: String,
                 defaultVisibilityTimeout: VisibilityTimeout,
                 created: DateTime,
                 lastModified: DateTime)

object Queue {
  val UnspecifiedDate = new DateTime(0)
  def apply(name: String, defaultVisibilityTimeout: VisibilityTimeout): Queue = {
    Queue(name, defaultVisibilityTimeout, UnspecifiedDate, UnspecifiedDate)
  }
}