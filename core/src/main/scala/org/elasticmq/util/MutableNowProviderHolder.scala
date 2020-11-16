package org.elasticmq.util

import org.joda.time.DateTime

trait MutableNowProviderHolder extends NowProviderHolder {

  override val nowProvider: NowProvider = new MutableNowProvider(
    new DateTime().withDate(2020, 1, 1).withTimeAtStartOfDay().getMillis
  )
}
