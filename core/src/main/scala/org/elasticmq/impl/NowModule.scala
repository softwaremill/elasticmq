package org.elasticmq.impl

import org.joda.time.DateTime

trait NowModule {
  def nowAsDateTime = new DateTime
  def now = nowAsDateTime.getMillis
}