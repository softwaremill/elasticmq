package org.elasticmq

sealed abstract class NextDelivery {
  def toMillis(nowMillis: Long): MillisNextDelivery
}

case class MillisNextDelivery(millis: Long) extends NextDelivery {
  def toMillis(nowMillis: Long) = this
}

case class AfterMillisNextDelivery(millis: Long) extends NextDelivery {
  def toMillis(nowMillis: Long) = MillisNextDelivery(nowMillis + millis)
}

object ImmediateNextDelivery extends NextDelivery {
  def toMillis(nowMillis: Long) = MillisNextDelivery(nowMillis)
}