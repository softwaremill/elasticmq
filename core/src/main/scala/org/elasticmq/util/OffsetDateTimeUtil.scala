package org.elasticmq.util

import java.time.{Instant, OffsetDateTime, ZoneOffset}

object OffsetDateTimeUtil {

  def ofEpochMilli(millis: Long): OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
}
