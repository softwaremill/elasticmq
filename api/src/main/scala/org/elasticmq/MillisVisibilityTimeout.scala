package org.elasticmq

sealed abstract class VisibilityTimeout

case class MillisVisibilityTimeout(millis: Long) extends VisibilityTimeout {
  val seconds = millis / 1000
}

object MillisVisibilityTimeout {
  def fromSeconds(seconds: Long) = MillisVisibilityTimeout(seconds * 1000)
}

object DefaultVisibilityTimeout extends VisibilityTimeout