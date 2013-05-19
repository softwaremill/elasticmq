package org.elasticmq

sealed abstract class NextDelivery {
  def toMillis(nowMillis: Long, queueDelay: Long): MillisNextDelivery
}

case class MillisNextDelivery(millis: Long) extends NextDelivery {
  def toMillis(nowMillis: Long, queueDelay: Long) = this
}

case class AfterMillisNextDelivery(millis: Long) extends NextDelivery {
  def toMillis(nowMillis: Long, queueDelay: Long) = MillisNextDelivery(nowMillis + millis)
}

object ImmediateNextDelivery extends NextDelivery {
  def toMillis(nowMillis: Long, queueDelay: Long) = MillisNextDelivery(nowMillis + queueDelay)
}