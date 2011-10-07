package org.elasticmq.storage.squeryl

import org.squeryl._
import PrimitiveTypeMode._
import org.elasticmq._
import org.joda.time.DateTime
import org.squeryl.annotations.Column

trait SquerylSchemaModule {
  def queues = MQSchema.queues
  def messages = MQSchema.messages
  def messageStatistics = MQSchema.messageStatistics

  def queuesToMessagesCond(m: SquerylMessage, q: SquerylQueue) = q.id === m.queueName

  object MQSchema extends Schema {
    val queues = table[SquerylQueue]("emq_queue")
    val messages = table[SquerylMessage]("emq_message")
    val messageStatistics = table[SquerylMessageStatistics]("emq_msg_stats")

    val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => queuesToMessagesCond(m, q))
    queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)

    val messagesToMessageStatistics = oneToManyRelation(messages, messageStatistics).via((m, ms) => m.id === ms.id)
    messagesToMessageStatistics.foreignKeyDeclaration.constrainReference(onDelete cascade)

    on(messages)(m => declare(
      m.content is(dbType("text"))
    ))
  }
}

// These must be top-level classes, because of Squeryl requirements

class SquerylQueue(val id: String,
                   @Column("default_visibility_timeout") val defaultVisibilityTimeout: Long,
                   @Column("created_timestamp") val createdTimestamp: Long,
                   @Column("last_modified_timestamp") val lastModifiedTimestamp: Long) extends KeyedEntity[String] {
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

class SquerylMessage(val id: String,
                     @Column("queue_name") val queueName: String,
                     val content: String,
                     @Column("next_delivery") val nextDelivery: Long,
                     @Column("created_timestamp") val createdTimestamp: Long) extends KeyedEntity[String] {
  def toMessage(q: SquerylQueue): SpecifiedMessage = Message(q.toQueue, Some(id), content,
    MillisNextDelivery(nextDelivery), new DateTime(createdTimestamp))
}

object SquerylMessage {
  def from(message: SpecifiedMessage) = {
    new SquerylMessage(message.id.get, message.queue.name, message.content, message.nextDelivery.millis,
      message.created.getMillis)
  }
}

class SquerylMessageStatistics(val id: String,
                               @Column("approximate_first_receive") val approximateFirstReceive: Long,
                               @Column("approximate_receive_count") val approximateReceiveCount: Int) extends KeyedEntity[String] {
  def toMessageStatistics(m: SpecifiedMessage) = MessageStatistics(m,
    if (approximateFirstReceive == 0) NeverReceived else OnDateTimeReceived(new DateTime(approximateFirstReceive)),
    approximateReceiveCount)
}

object SquerylMessageStatistics {
  def from(statistics: MessageStatistics) = {
    val receivedTimestamp = statistics.approximateFirstReceive match {
      case NeverReceived => 0
      case OnDateTimeReceived(dt) => dt.getMillis
    }

    new SquerylMessageStatistics(statistics.message.id.get,
      receivedTimestamp,
      statistics.approximateReceiveCount)
  }
}
