package org.elasticmq.util

import java.util.concurrent.atomic.AtomicLong

import org.joda.time.DateTime

class MutableNowProvider(initalValue: Long = 100L) extends NowProvider {
  var mutableNowMillis = new AtomicLong(initalValue)

  override def nowMillis = mutableNowMillis.get()

  override def now = new DateTime(nowMillis)
}


