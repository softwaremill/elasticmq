package org.elasticmq

sealed case class MillisVisibilityTimeout(millis: Long) {
  val seconds = millis / 1000
}

object MillisVisibilityTimeout {
  def fromSeconds(seconds: Long) = MillisVisibilityTimeout(seconds * 1000)
}