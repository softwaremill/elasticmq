package org.elasticmq.storage.squeryl

import org.squeryl._
import PrimitiveTypeMode._
import org.elasticmq._

trait SquerylSchemaModule {
  def queues = MQSchema.queues
  def messages = MQSchema.messages

  def queuesToMessagesCond(m: SquerylMessage, q: SquerylQueue) = q.id === m.queueName

  object MQSchema extends Schema {
    val queues = table[SquerylQueue]
    val messages = table[SquerylMessage]

    val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => queuesToMessagesCond(m, q))
    queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)
  }
}

// These must be top-level classes, because of Squeryl requirements

class SquerylQueue(val id: String, val defaultVisibilityTimeout: Long) extends KeyedEntity[String] {
  def toQueue = Queue(id, VisibilityTimeout(defaultVisibilityTimeout))
}

object SquerylQueue {
  def from(queue: Queue) = new SquerylQueue(queue.name, queue.defaultVisibilityTimeout.millis)
}

class SquerylMessage(val id: String, val queueName: String, val content: String,
                     val nextDelivery: Long) extends KeyedEntity[String] {
  def toMessage(q: SquerylQueue): SpecifiedMessage = Message(q.toQueue, Some(id), content, MillisNextDelivery(nextDelivery))
}

object SquerylMessage {
  def from(message: SpecifiedMessage) = {
    new SquerylMessage(message.id.get, message.queue.name, message.content, message.nextDelivery.millis)
  }
}