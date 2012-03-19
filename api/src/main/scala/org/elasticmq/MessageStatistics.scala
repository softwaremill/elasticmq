package org.elasticmq

import org.joda.time.DateTime

case class MessageStatistics(approximateFirstReceive: Received,
                             approximateReceiveCount: Int)

object MessageStatistics {
  val empty = MessageStatistics(NeverReceived, 0)
}

sealed trait Received
case class OnDateTimeReceived(when: DateTime) extends Received
case object NeverReceived extends Received