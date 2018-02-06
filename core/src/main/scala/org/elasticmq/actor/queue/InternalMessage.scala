/*
 * All right reserved - Fronteer LTD
 */
package org.elasticmq.actor.queue

import java.util.UUID

import org.elasticmq.{DeliveryReceipt, MessageAttribute, MessageData, MessageId, MessageStatistics, MillisNextDelivery, NeverReceived, NewMessageData, QueueData, Received}
import org.joda.time.DateTime

case class InternalMessage(id: String,
    var deliveryReceipt: Option[String],
    var nextDelivery: Long,
    content: String,
    messageAttributes: Map[String, MessageAttribute],
    created: DateTime,
    var firstReceive: Received,
    var receiveCount: Int,
    isFifo: Boolean,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[String])
    extends Comparable[InternalMessage] {

  // Priority queues have biggest elements first
  def compareTo(other: InternalMessage): Int = {
    if (isFifo) {
      -created.getMillis.compareTo(other.created.getMillis)
    } else {
      -nextDelivery.compareTo(other.nextDelivery)
    }
  }

  def toMessageData = MessageData(
    MessageId(id),
    deliveryReceipt.map(DeliveryReceipt(_)),
    content,
    messageAttributes,
    MillisNextDelivery(nextDelivery),
    created,
    MessageStatistics(firstReceive, receiveCount),
    messageGroupId,
    messageDeduplicationId)

  def toNewMessageData = NewMessageData(
    Some(MessageId(id)),
    content,
    messageAttributes,
    MillisNextDelivery(nextDelivery),
    messageGroupId,
    messageDeduplicationId)

  def deliverable(deliveryTime: Long): Boolean = nextDelivery <= deliveryTime
}

object InternalMessage {

  def from(newMessageData: NewMessageData, queueData: QueueData): InternalMessage = {
    val now = System.currentTimeMillis()
    new InternalMessage(
      newMessageData.id.getOrElse(generateId()).id,
      None,
      newMessageData.nextDelivery.toMillis(now, queueData.delay.getMillis).millis,
      newMessageData.content,
      newMessageData.messageAttributes,
      new DateTime(),
      NeverReceived,
      0,
      queueData.isFifo,
      newMessageData.messageGroupId,
      newMessageData.messageDeduplicationId)
  }

  private def generateId() = MessageId(UUID.randomUUID().toString)
}

