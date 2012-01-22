package org.elasticmq

import org.joda.time.{DateTime, Duration}

trait Queue extends QueueOperations {
  def name: String
  def defaultVisibilityTimeout: MillisVisibilityTimeout
  def delay: Duration
  def created: DateTime
  def lastModified: DateTime
}

case class QueueBuilder private (name: String, defaultVisibilityTimeout: VisibilityTimeout, delay: Duration) {
  def withDefaultVisibilityTimeout(defaultVisibilityTimeout: VisibilityTimeout) = this.copy(defaultVisibilityTimeout = defaultVisibilityTimeout)
  def withDelay(duration: Duration) = this.copy(delay = delay)
}

object QueueBuilder {
  def apply(name: String): QueueBuilder = QueueBuilder(name, DefaultVisibilityTimeout, Duration.ZERO)
}