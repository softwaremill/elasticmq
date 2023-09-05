package org.elasticmq.util

import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong

class MutableNowProvider(initalValue: Long = 100L) extends NowProvider {
  var mutableNowMillis = new AtomicLong(initalValue)

  override def nowMillis: Long = mutableNowMillis.get()

  override def now: OffsetDateTime = OffsetDateTimeUtil.ofEpochMilli(nowMillis)
}
