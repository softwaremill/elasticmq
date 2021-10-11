package org.elasticmq.persistence.sql

import org.elasticmq._
import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.Logging
import org.joda.time.DateTime
import scalikejdbc._
import spray.json._

class MessageRepository(queueName: String, persistenceConfig: SqlQueuePersistenceConfig) extends Logging {

  implicit val session: AutoSession = AutoSession

  SqlPersistence.initializeSingleton(persistenceConfig)

  private val hashHex = queueName.hashCode.toHexString
  private val escapedName = queueName.replace(".", "_").replace("-", "_")
  private val tableName = SQLSyntax.createUnsafely(s"message_${escapedName}_${hashHex}")

  if (persistenceConfig.pruneDataOnInit) {
    logger.debug(s"Deleting stored messages for queue $queueName")
    sql"drop table if exists $tableName".execute.apply()
  }

  sql"""
    create table if not exists $tableName (
      message_id varchar unique,
      delivery_receipts blob,
      next_delivery integer(8),
      content blob,
      attributes blob,
      created integer(8),
      received integer(8),
      receive_count integer(4),
      group_id varchar,
      deduplication_id varchar,
      tracing_id varchar,
      sequence_number varchar
    )""".execute.apply()

  case class SerializableAttribute(key: String, primaryDataType: String, stringValue: String, customType: Option[String])

  object SerializableAttributeProtocol extends DefaultJsonProtocol {
    implicit val colorFormat: JsonFormat[SerializableAttribute] = jsonFormat4(SerializableAttribute)
  }

  import SerializableAttributeProtocol._

  case class DBMessage(messageId: String,
                       deliveryReceipts: String,
                       nextDelivery: Long,
                       content: String,
                       attributes: String,
                       created: Long,
                       received: Option[Long],
                       receiveCount: Int,
                       groupId: Option[String],
                       deduplicationId: Option[String],
                       tracingId: Option[String],
                       sequenceNumber: Option[String]) {

    def toInternalMessage: InternalMessage = {
      val serializedAttrs = attributes.parseJson.convertTo[List[SerializableAttribute]].map { attr =>
        (attr.key, attr.primaryDataType match {
          case "String" => StringMessageAttribute(attr.stringValue, attr.customType)
          case "Number" => NumberMessageAttribute(attr.stringValue, attr.customType)
          case "Binary" => BinaryMessageAttribute.fromBase64(attr.stringValue, attr.customType)
        })
      } toMap

      val serializedDeliveryReceipts = deliveryReceipts.parseJson.convertTo[List[String]]

      val firstReceive = received.map(time => OnDateTimeReceived(new DateTime(time))).getOrElse(NeverReceived)

      InternalMessage(
        messageId,
        serializedDeliveryReceipts.toBuffer,
        nextDelivery,
        content,
        serializedAttrs,
        new DateTime(created),
        orderIndex = 0,
        firstReceive,
        receiveCount,
        isFifo = false,
        groupId,
        deduplicationId.map(id => DeduplicationId(id)),
        tracingId.map(TracingId),
        sequenceNumber)
    }
  }

  object DBMessage extends SQLSyntaxSupport[DBMessage] {
    override val tableName = s"message_$queueName"

    def apply(rs: WrappedResultSet) = new DBMessage(
      rs.string("message_id"),
      rs.string("delivery_receipts"),
      rs.long("next_delivery"),
      rs.string("content"),
      rs.string("attributes"),
      rs.long("created"),
      rs.longOpt("received"),
      rs.int("receive_count"),
      rs.stringOpt("group_id"),
      rs.stringOpt("deduplication_id"),
      rs.stringOpt("tracing_id"),
      rs.stringOpt("sequence_number")
    )

    def from(message: InternalMessage): DBMessage = {
      val deliveryReceipts = message.deliveryReceipts.toList

      val attributes = message.messageAttributes.toList.map {
        case (k, v) =>
          v match {
            case StringMessageAttribute(stringValue, customType) => SerializableAttribute(k, "String", stringValue, customType)
            case NumberMessageAttribute(stringValue, customType) => SerializableAttribute(k, "Number", stringValue, customType)
            case attr: BinaryMessageAttribute => SerializableAttribute(k, "Binary", attr.asBase64, attr.customType)
          }
      }

      val received = message.firstReceive match {
        case NeverReceived => None
        case OnDateTimeReceived(when) => Some(when.toInstant.getMillis)
      }

      val deduplicationId = message.messageDeduplicationId.map(_.id)

      new DBMessage(
        message.id,
        deliveryReceipts.toJson.toString,
        message.nextDelivery,
        message.content,
        attributes.toJson.toString,
        message.created.toInstant.getMillis,
        received,
        message.receiveCount,
        message.messageGroupId,
        deduplicationId,
        message.tracingId.map(_.id),
        message.sequenceNumber)
    }
  }

  def drop(): Unit = {
    sql"drop table if exists $tableName".execute.apply()
  }

  def findAll(): List[InternalMessage] = {
    DB localTx { implicit session =>
      sql"select * from $tableName"
        .map(rs => DBMessage(rs)).list.apply()
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
