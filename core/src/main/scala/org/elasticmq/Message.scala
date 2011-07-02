package org.elasticmq

case class Message(queue: Queue, id: String, content: String, visibilityTimeout: VisibilityTimeout, lastDelivered: Long)

object Message {
  def apply(queue: Queue, content: String): Message = Message(queue, null, content, DefaultVisibilityTimeout, 0)
}