package org.elasticmq.storage

import org.elasticmq._
import org.elasticmq.data.MessageData

trait MessageStorageModule {
  trait MessageStorage {
    def persistMessage(message: MessageData)
    def updateVisibilityTimeout(messageId: MessageId, newNextDelivery: MillisNextDelivery)

    def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData]
    def deleteMessage(messageId: MessageId)

    def lookupMessage(messageId: MessageId): Option[MessageData]
  }

  def messageStorage(queueName: String): MessageStorage
}