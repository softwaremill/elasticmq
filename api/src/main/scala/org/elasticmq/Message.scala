package org.elasticmq

import org.joda.time.DateTime
import java.util.UUID

trait Message extends MessageOperations {
  def content: String
  def id: MessageId
  def nextDelivery: MillisNextDelivery
  def created: DateTime
  def lastDeliveryReceipt: Option[DeliveryReceipt]
  
  // Java-style
  def getContent = content
  def getId = id
  def getNextDelivery = nextDelivery
  def getCreated = created
  def getLastDeliveryReceipt = lastDeliveryReceipt
}

sealed case class MessageId(id: String) {
  override def toString = id
}

sealed case class DeliveryReceipt(receipt: String) {
  override def toString = receipt
}

object DeliveryReceipt {
  def generate = new DeliveryReceipt(UUID.randomUUID().toString)
}

case class MessageBuilder private (content: String, id: Option[MessageId], nextDelivery: NextDelivery) {
  def withId(id: String) = this.copy(id = Some(MessageId(id)))
  def withNextDelivery(nextDelivery: NextDelivery) = this.copy(nextDelivery = nextDelivery)
}

object MessageBuilder {
  def apply(content: String): MessageBuilder = MessageBuilder(content, None, ImmediateNextDelivery)
}