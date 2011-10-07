package org.elasticmq

import org.joda.time.DateTime

case class MessageStatistics(message: SpecifiedMessage,
                             approximateFirstReceive: Received,
                             approximateReceiveCount: Int)

sealed abstract class Received
case class OnDateTimeReceived(when: DateTime) extends Received
object NeverReceived extends Received