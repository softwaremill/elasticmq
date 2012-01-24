package org.elasticmq

import org.joda.time.{DateTime, Duration}

trait Queue extends QueueOperations {
  def name: String
  def defaultVisibilityTimeout: MillisVisibilityTimeout
  def delay: Duration
  def created: DateTime
  def lastModified: DateTime
}

case class QueueBuilder private (name: String, defaultVisibilityTimeout: MillisVisibilityTimeout, delay: Duration) {
  def withDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout) =
    this.copy(defaultVisibilityTimeout = defaultVisibilityTimeout)

  def withDelay(duration: Duration) = this.copy(delay = delay)
}

object QueueBuilder {
  val DefaultVisibilityTimeout = 10000L

  def apply(name: String): QueueBuilder = QueueBuilder(
    name,
    MillisVisibilityTimeout(DefaultVisibilityTimeout),
    Duration.ZERO)
}