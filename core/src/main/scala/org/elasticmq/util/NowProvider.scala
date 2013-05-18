package org.elasticmq.util

import org.joda.time.DateTime

class NowProvider {
  def nowMillis = System.currentTimeMillis()
  def now = new DateTime()
}
