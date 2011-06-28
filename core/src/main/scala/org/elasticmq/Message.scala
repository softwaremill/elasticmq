package org.elasticmq

case class Message(queue: Queue, id: String, content: String, visibilityTimeout: Long)