package org.elasticmq.persistence.sql

import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.Logging

class MessageRepository(queueName: String, db: DB) extends Logging {

  import scalikejdbc._
  implicit val session: AutoSession = AutoSession

  private val hashHex = queueName.hashCode.toHexString
  private val escapedName = queueName.replace(".", "_").replace("-", "_")
  private val tableName = SQLSyntax.createUnsafely(s"message_${escapedName}_${hashHex}")

  if (db.persistenceConfig.pruneDataOnInit) {
    logger.debug(s"Deleting stored messages for queue $queueName")
    sql"drop table if exists $tableName".execute.apply()
  }

  sql"""
    create table if not exists $tableName (
      message_id longtext unique,
      delivery_receipts blob,
      next_delivery bigint,
      content blob,
      attributes blob,
      created bigint,
      received bigint,
      receive_count int,
      group_id longtext,
      deduplication_id longtext,
      tracing_id longtext,
      sequence_number longtext
    )""".execute.apply()

  def drop(): Unit = {
    sql"drop table if exists $tableName".execute.apply()
  }

  def findAll(): List[InternalMessage] = {
    DB localTx { implicit session =>
      sql"select * from $tableName"
        .map(rs => DBMessage(rs))
        .list
        .apply()
        .map(_.toInternalMessage)
    }
  }

  def add(internalMessage: InternalMessage): Int = {
    val message = DBMessage.from(internalMessage)
    sql"""insert into $tableName
           (message_id, delivery_receipts, next_delivery, content, attributes, created, received, receive_count, group_id, deduplication_id, tracing_id, sequence_number)
           values (${message.messageId},
                   ${message.deliveryReceipts},
                   ${message.nextDelivery},
                   ${message.content},
                   ${message.attributes},
                   ${message.created},
                   ${message.received},
                   ${message.receiveCount},
                   ${message.groupId},
                   ${message.deduplicationId},
                   ${message.tracingId},
                   ${message.sequenceNumber})""".update.apply
  }

  def update(internalMessage: InternalMessage): Int = {
    val message = DBMessage.from(internalMessage)
    sql"""update $tableName set
                    delivery_receipts = ${message.deliveryReceipts},
                    next_delivery = ${message.nextDelivery},
                    attributes = ${message.attributes},
                    received = ${message.received},
                    receive_count = ${message.receiveCount},
                    tracing_id = ${message.tracingId},
                    sequence_number = ${message.sequenceNumber}
              where message_id = ${message.messageId}""".update.apply
  }

  def remove(messageId: String): Int = {
    sql"delete from $tableName where message_id = $messageId".update.apply
  }
}
