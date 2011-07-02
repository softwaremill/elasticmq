package org.elasticmq

case class Message(queue: Queue, id: String, content: String, visibilityTimeout: Long, lastDelivered: Long) {
  def this(queue: Queue, content: String) = this(queue, null, content, 0, 0)
}