package org.elasticmq

sealed case class VisibilityTimeout(millis: Long) {
  val seconds = millis / 1000
}

object VisibilityTimeout {
  def fromSeconds(seconds: Long) = VisibilityTimeout(seconds * 1000)
}
