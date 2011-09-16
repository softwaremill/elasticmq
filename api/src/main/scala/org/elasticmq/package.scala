package org

package object elasticmq {
  type AnyMessage = Message[Option[String], NextDelivery]
  type IdentifiableMessage = Message[Some[String], NextDelivery]
  type SpecifiedMessage = Message[Some[String], MillisNextDelivery]
}