package org

package object elasticmq {
  type AnyMessage = Message[NextDelivery]
  type SpecifiedMessage = Message[MillisNextDelivery]
}