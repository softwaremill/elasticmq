package org.elasticmq.actor.test

import org.joda.time.DateTime
import org.elasticmq.util.NowProvider
import java.util.concurrent.atomic.AtomicLong

class MutableNowProvider extends NowProvider {
  var mutableNowMillis = new AtomicLong(100L)

  override def nowMillis = mutableNowMillis.get()
  override def now = new DateTime(nowMillis)
}
