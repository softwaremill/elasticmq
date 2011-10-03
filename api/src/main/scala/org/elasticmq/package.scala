package org

import org.joda.time.DateTime

package object elasticmq {
  type AnyMessage = Message[Option[String], NextDelivery]
  type IdentifiableMessage = Message[Some[String], NextDelivery]
  type SpecifiedMessage = Message[Some[String], MillisNextDelivery]

  val UnspecifiedDate = new DateTime(0)
}