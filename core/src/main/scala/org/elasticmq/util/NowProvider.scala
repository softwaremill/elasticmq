package org.elasticmq.util

import java.time.OffsetDateTime

class NowProvider {
  def nowMillis: Long = System.currentTimeMillis()
  def now: OffsetDateTime = OffsetDateTime.now()
}
