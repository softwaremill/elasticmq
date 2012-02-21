package org.elasticmq.storage.interfaced

import org.elasticmq._
import org.elasticmq.data.MessageData

trait MessagesStorage {
  def sendMessage(message: MessageData)
  def updateVisibilityTimeout(messageId: MessageId, newNextDelivery: MillisNextDelivery)
  def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData]
  def deleteMessage(messageId: MessageId)
  def lookupMessage(messageId: MessageId): Option[MessageData]
}
