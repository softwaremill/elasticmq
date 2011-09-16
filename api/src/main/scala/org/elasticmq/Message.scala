package org.elasticmq

case class Message[+NEXT_DELIVERY <: NextDelivery](queue: Queue, id: String, content: String, nextDelivery: NEXT_DELIVERY)

object Message {
  def apply(queue: Queue, content: String): Message[NextDelivery] =
    Message[NextDelivery](queue, null, content, ImmediateNextDelivery)
}