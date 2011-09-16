package org.elasticmq.impl

import org.joda.time.DateTime

trait NowModule {
  def now = (new DateTime).getMillis
}