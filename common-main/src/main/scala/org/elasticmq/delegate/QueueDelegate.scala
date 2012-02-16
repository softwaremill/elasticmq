package org.elasticmq.delegate

import org.elasticmq._

trait QueueDelegate extends QueueOperationsDelegate with Queue {
  val delegate: Queue

  def name = delegate.name

  def defaultVisibilityTimeout = delegate.defaultVisibilityTimeout

  def delay = delegate.delay

  def created = delegate.created

  def lastModified = delegate.lastModified
}
