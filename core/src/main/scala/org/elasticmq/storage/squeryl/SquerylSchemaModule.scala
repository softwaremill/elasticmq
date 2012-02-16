package org.elasticmq.storage.squeryl

import org.squeryl._
import PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.data.{MessageData, QueueData}
import org.squeryl.annotations.Column
import org.joda.time.{Duration, DateTime}

trait SquerylSchemaModule {
  def queues = MQSchema.queues
  def messages = MQSchema.messages
  def messageStatistics = MQSchema.messageStatistics

  def queuesToMessagesCond(m: SquerylMessage, q: SquerylQueue) = q.id === m.queueName

  object MQSchema extends Schema {
    val queues = table[SquerylQueue]("emq_queue")
    val messages = table[SquerylMessage]("emq_message")
    val messageStatistics = table[SquerylMessageStatistics]("emq_msg_stats")

    on(messages)(m => declare(
      m.content is(dbType("text"))
    ))

    val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => q.id === m.queueName)
    queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)

    val messagesToMessageStatistics = oneToManyRelation(messages, messageStatistics).via((m, ms) => m.id === ms.id)
    messagesToMessageStatistics.foreignKeyDeclaration.constrainReference(onDelete cascade)
  }
}

// These must be top-level classes, because of Squeryl requirements

class SquerylQueue(val id: String,
                   @Column("default_visibility_timeout") val defaultVisibilityTimeout: Long,
                   @Column("delay") val delay: Long,
                   @Column("created_timestamp") val createdTimestamp: Long,
                   @Column("last_modified_timestamp") val lastModifiedTimestamp: Long) extends KeyedEntity[String] {
  def toQueue = QueueData(id,
    MillisVisibilityTimeout(defaultVisibilityTimeout),
    new Duration(delay),
    new DateTime(createdTimestamp),
    new DateTime(lastModifiedTimestamp))
}

object SquerylQueue {
  def from(queue: QueueData) = new SquerylQueue(queue.name,
    queue.defaultVisibilityTimeout.millis,
    queue.delay.getMillis,
    queue.created.getMillis,
    queue.lastModified.getMillis)
}

class SquerylMessage(val id: String,
                     @Column("queue_name") val queueName: String,
                     val content: String,
                     @Column("next_delivery") val nextDelivery: Long,
                     @Column("created_timestamp") val createdTimestamp: Long) extends KeyedEntity[String] {
  def toMessage: MessageData = MessageData(MessageId(id), content,
    MillisNextDelivery(nextDelivery), new DateTime(createdTimestamp))
}

object SquerylMessage {
  def from(queueName: String, message: MessageData) = {
    new SquerylMessage(message.id.id, queueName, message.content, message.nextDelivery.millis,
      message.created.getMillis)
  }
}

class SquerylMessageStatistics(val id: String,
                               @Column("approximate_first_receive") val approximateFirstReceive: Long,
                               @Column("approximate_receive_count") val approximateReceiveCount: Int) extends KeyedEntity[String] {
  def toMessageStatistics = MessageStatistics(
    if (approximateFirstReceive == 0) NeverReceived else OnDateTimeReceived(new DateTime(approximateFirstReceive)),
    approximateReceiveCount)
}

object SquerylMessageStatistics {
  def from(messageId: MessageId, statistics: MessageStatistics) = {
    val receivedTimestamp = statistics.approximateFirstReceive match {
      case NeverReceived => 0
      case OnDateTimeReceived(dt) => dt.getMillis
    }

    new SquerylMessageStatistics(messageId.id,
      receivedTimestamp,
      statistics.approximateReceiveCount)
  }
}
