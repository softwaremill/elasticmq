package org.elasticmq.storage

import org.elasticmq._

trait MessageStorageModule {
  trait MessageStorage {
    def persistMessage(message: SpecifiedMessage)
    def updateMessage(message: SpecifiedMessage)

    def receiveMessage(queue: Queue, deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[SpecifiedMessage]
    def deleteMessage(message: IdentifiableMessage)

    def lookupMessage(id: String): Option[SpecifiedMessage]
  }

  def messageStorage: MessageStorage
}