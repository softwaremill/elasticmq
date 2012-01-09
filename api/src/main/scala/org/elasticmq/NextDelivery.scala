package org.elasticmq

sealed abstract class NextDelivery
case class MillisNextDelivery(millis: Long) extends NextDelivery
case class AfterMillisNextDelivery(millis: Long) extends NextDelivery
object ImmediateNextDelivery extends NextDelivery