package org.elasticmq.persistence.sql

import org.elasticmq._
import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.persistence.sql.SerializableAttributeProtocol._
import org.joda.time.DateTime
import scalikejdbc.WrappedResultSet
import spray.json._

case class DBMessage(
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
    sequenceNumber: Option[String]
) {

  def toInternalMessage: InternalMessage = {
    val serializedAttrs = attributes.parseJson
      .convertTo[List[SerializableAttribute]]
      .map { attr =>
        (
          attr.key,
          attr.primaryDataType match {
            case "String" => StringMessageAttribute(attr.stringValue, attr.customType)
            case "Number" => NumberMessageAttribute(attr.stringValue, attr.customType)
            case "Binary" => BinaryMessageAttribute.fromBase64(attr.stringValue, attr.customType)
          }
        )
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
      sequenceNumber
    )
  }
}

object DBMessage {

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

    val attributes = message.messageAttributes.toList.map { case (k, v) =>
      v match {
        case StringMessageAttribute(stringValue, customType) =>
          SerializableAttribute(k, "String", stringValue, customType)
        case NumberMessageAttribute(stringValue, customType) =>
          SerializableAttribute(k, "Number", stringValue, customType)
        case attr: BinaryMessageAttribute => SerializableAttribute(k, "Binary", attr.asBase64, attr.customType)
      }
    }

    val received = message.firstReceive match {
      case NeverReceived            => None
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
      message.sequenceNumber
    )
  }
}
