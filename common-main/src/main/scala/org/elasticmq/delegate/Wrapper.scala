package org.elasticmq.delegate

import org.elasticmq.{Message, MessageOperations, Queue, QueueOperations}

trait Wrapper {
  def wrapQueueOperations(queueOperations: QueueOperations): QueueOperations
  def wrapQueue(queue: Queue): Queue
  def wrapMessageOperations(messageOperations: MessageOperations): MessageOperations
  def wrapMessage(message: Message): Message
}

object IdentityWrapper extends Wrapper {
  def wrapQueueOperations(queueOperations: QueueOperations) = queueOperations
  def wrapQueue(queue: Queue) = queue
  def wrapMessageOperations(messageOperations: MessageOperations) = messageOperations
  def wrapMessage(message: Message) = message
}
