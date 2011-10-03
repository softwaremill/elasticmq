package org.elasticmq.storage.squeryl

import org.squeryl._
import PrimitiveTypeMode._
import org.elasticmq._
import org.joda.time.DateTime

trait SquerylSchemaModule {
  def queues = MQSchema.queues
  def messages = MQSchema.messages

  def queuesToMessagesCond(m: SquerylMessage, q: SquerylQueue) = q.id === m.queueName

  object MQSchema extends Schema {
    val queues = table[SquerylQueue]("emq_queue")
    val messages = table[SquerylMessage]("emq_message")

    val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => queuesToMessagesCond(m, q))
    queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)

    on(messages)(m => declare(
      m.content is(dbType("text"))
    ))
  }
}

// These must be top-level classes, because of Squeryl requirements

class SquerylQueue(val id: String,
                   val defaultVisibilityTimeout: Long,
                   val createdTimestamp: Long,
                   val lastModifiedTimestamp: Long) extends KeyedEntity[String] {
  def toQueue = Queue(id,
    VisibilityTimeout(defaultVisibilityTimeout),
    new DateTime(createdTimestamp),
    new DateTime(lastModifiedTimestamp))
}

object SquerylQueue {
  def from(queue: Queue) = new SquerylQueue(queue.name,
    queue.defaultVisibilityTimeout.millis,
    queue.created.getMillis,
    queue.lastModified.getMillis)
}

class SquerylMessage(val id: String, val queueName: String, val content: String,
                     val nextDelivery: Long, val createdTimestamp: Long) extends KeyedEntity[String] {
  def toMessage(q: SquerylQueue): SpecifiedMessage = Message(q.toQueue, Some(id), content,
    MillisNextDelivery(nextDelivery), new DateTime(createdTimestamp))
}

object SquerylMessage {
  def from(message: SpecifiedMessage) = {
    new SquerylMessage(message.id.get, message.queue.name, message.content, message.nextDelivery.millis,
      message.created.getMillis)
  }
}