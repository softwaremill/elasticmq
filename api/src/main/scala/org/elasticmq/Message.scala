package org.elasticmq

import org.joda.time.DateTime

case class Message[+ID <: Option[String], +NEXT_DELIVERY <: NextDelivery](queue: Queue,
                                                                          id: ID,
                                                                          content: String,
                                                                          nextDelivery: NEXT_DELIVERY,
                                                                          created: DateTime)

object Message {
  def apply[ID <: Option[String], NEXT_DELIVERY <: NextDelivery](queue: Queue, id: ID, content: String,
                                           nextDelivery: NEXT_DELIVERY): Message[ID, NEXT_DELIVERY] =
    Message(queue, id, content, nextDelivery, UnspecifiedDate)

  def apply[NEXT_DELIVERY <: NextDelivery](queue: Queue, id: String, content: String,
                                           nextDelivery: NEXT_DELIVERY): Message[Some[String], NEXT_DELIVERY] =
    Message(queue, Some(id), content, nextDelivery)

  def apply(queue: Queue, content: String): Message[Option[String], NextDelivery] =
    Message[Option[String], NextDelivery](queue, None, content, ImmediateNextDelivery)
}