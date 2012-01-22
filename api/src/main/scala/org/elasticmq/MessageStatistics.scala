package org.elasticmq

import org.joda.time.DateTime

case class MessageStatistics(approximateFirstReceive: Received,
                             approximateReceiveCount: Int)

object MessageStatistics {
  val empty = MessageStatistics(NeverReceived, 0)
}

sealed abstract class Received
case class OnDateTimeReceived(when: DateTime) extends Received
object NeverReceived extends Received