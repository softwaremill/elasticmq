package org.elasticmq

import java.time.OffsetDateTime

case class MessageStatistics(approximateFirstReceive: Received, approximateReceiveCount: Int)

object MessageStatistics {
  val empty = MessageStatistics(NeverReceived, 0)
}

sealed trait Received
case class OnDateTimeReceived(when: OffsetDateTime) extends Received
case object NeverReceived extends Received
