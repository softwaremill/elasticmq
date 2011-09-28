package org.elasticmq

sealed case class VisibilityTimeout(millis: Long) {
  val seconds = millis / 1000
}
