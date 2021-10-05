package org.elasticmq

import java.util.concurrent.atomic.AtomicLong

object SequenceNumber {
  private val current = new AtomicLong

  def next(): String = current.getAndIncrement().toString
}
