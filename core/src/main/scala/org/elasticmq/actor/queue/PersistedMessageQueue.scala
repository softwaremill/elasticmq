package org.elasticmq.actor.queue

import org.elasticmq._
import org.elasticmq.util.{Logging, NowProvider}
import org.joda.time.DateTime
import scalikejdbc._
import spray.json._

object PersistedMessageQueue {

  private var initialized = false

  def initializedSingleton(persistenceConfig: MessagePersistenceConfig): Unit = {
    if (!initialized) {
      Class.forName(persistenceConfig.driverClass)
      ConnectionPool.singleton(persistenceConfig.uri, persistenceConfig.username, persistenceConfig.password)
      initialized = true
    }
  }
}

class PersistedMessageQueue(name: String, persistenceConfig: MessagePersistenceConfig, isFifo: Boolean)(implicit nowProvider: NowProvider)
  extends SimpleMessageQueue with Logging {

  implicit val session: AutoSession = AutoSession

  PersistedMessageQueue.initializedSingleton(persistenceConfig)

  private val hashHex = name.hashCode.toHexString
  private val escapedName = name.replace(".", "_").replace("-", "_")
  private val tableName = SQLSyntax.createUnsafely(s"message_${escapedName}_${hashHex}")

  if (persistenceConfig.pruneDataOnInit) {
    sql"drop table if exists $tableName".execute.apply()
  }

  sql"""
    create table if not exists $tableName (
      id integer primary key autoincrement,
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

  case class DBMessage(id: Long,
                       messageId: String,
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
        sequenceNumber,
        Some(id))
    }
  }

  object DBMessage extends SQLSyntaxSupport[DBMessage] {
    override val tableName = s"message_$name"

    def apply(rs: WrappedResultSet) = new DBMessage(
      rs.long("id"),
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
  }

  override def onDelete(): Unit = {
    sql"drop table if exists $tableName".execute.apply()
  }

  override def +=(message: InternalMessage): Unit = {
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
    val deliveryReceipts = message.deliveryReceipts.toList

    val updateCount = message.persistedId match {
      case Some(persistedId) =>
        sql"""update $tableName set
                    delivery_receipts = ${deliveryReceipts.toJson.toString},
                    next_delivery = ${message.nextDelivery},
                    attributes = ${attributes.toJson.toString},
                    received = $received,
                    receive_count = ${message.receiveCount},
                    tracing_id = ${message.tracingId.map(_.id)},
                    sequence_number = ${message.sequenceNumber}
              where id = $persistedId""".update.apply
      case None =>
        sql"""insert into $tableName
           (message_id, delivery_receipts, next_delivery, content, attributes, created, received, receive_count, group_id, deduplication_id, tracing_id, sequence_number)
           values (${message.id},
                   ${deliveryReceipts.toJson.toString},
                   ${message.nextDelivery},
                   ${message.content},
                   ${attributes.toJson.toString},
                   ${message.created.toInstant.getMillis},
                   $received,
                   ${message.receiveCount},
                   ${message.messageGroupId},
                   $deduplicationId,
                   ${message.tracingId.map(_.id)},
                   ${message.sequenceNumber})""".update.apply
    }

    if (updateCount != 1) {
      // TODO: handle error
    }
  }

  override def byId: Map[String, InternalMessage] = {
    DB localTx { implicit session =>
      sql"select * from $tableName order by id"
        .map(rs => DBMessage(rs)).list.apply()
        .map(_.toInternalMessage)
        .map(msg => (msg.id, msg))
        .toMap
    }
  }

  override def clear(): Unit = {
    sql"delete from $tableName".update.apply
  }

  override def remove(messageId: String): Unit = {
    sql"delete from $tableName where message_id = ${messageId}".update.apply
  }

  override def inMemory: Boolean = false

  override def filterNot(p: InternalMessage => Boolean): MessageQueue = ???

  override def dequeue(count: Int, deliveryTime: Long): List[InternalMessage] = {
    val now = nowProvider.now.toInstant.getMillis

    if (isFifo) {
      dequeueFifo(count, now)
    } else {
      dequeueNonFifo(count, now)
    }
  }

  private def dequeueFifo(count: Int, now: Long) = {
    DB localTx { implicit session =>
      val messages = sql"select * from $tableName order by id".map(rs => DBMessage(rs)).list.apply()
        .map(_.toInternalMessage)

      val filteredMessages = messages.groupBy(_.messageGroupId.get).filter {
        case (_, messagesInGroup) => messagesInGroup.head.nextDelivery <= now
      }.toList.flatMap(_._2).take(count)

      filteredMessages
    }
  }

  private def dequeueNonFifo(count: Int, now: Long) = {
    DB localTx { implicit session =>
      val messages = sql"select * from $tableName where next_delivery <= $now order by id limit $count"
        .map(rs => DBMessage(rs)).list.apply()
        .map(_.toInternalMessage)

      messages
    }
  }
}
