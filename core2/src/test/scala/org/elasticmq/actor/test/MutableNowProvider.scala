package org.elasticmq.actor.test

import org.joda.time.DateTime
import org.elasticmq.util.NowProvider

class MutableNowProvider extends NowProvider {
  var _nowMillis = 100L

  override def nowMillis = _nowMillis
  override def now = new DateTime(nowMillis)
}
