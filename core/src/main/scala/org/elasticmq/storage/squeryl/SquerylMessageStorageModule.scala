package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.storage.MessageStorageModule
import org.elasticmq.impl.MessageData

trait SquerylMessageStorageModule extends MessageStorageModule {
  this: SquerylSchemaModule with SquerylMessageStatisticsStorageModule =>

  class SquerylMessageStorage(queueName: String) extends MessageStorage {
    def persistMessage(message: MessageData) {
      transaction {
        messages.insert(SquerylMessage.from(queueName, message))
        messageStatisticsStorage(queueName).writeMessageStatistics(message.id, MessageStatistics.empty, false)
      }
    }

    def updateVisibilityTimeout(messageId: MessageId, newNextDelivery: MillisNextDelivery) {
      transaction {
        update(messages)(m =>
          where(m.id === messageId.id)
            set(m.nextDelivery := newNextDelivery.millis))
      }
    }

    def deleteMessage(id: MessageId) {
      transaction {
        val messageId = id.id
        messages.delete(messageId)
        messageStatistics.delete(messageId)
      }
    }

    def lookupMessage(id: MessageId) = {
      transaction {
        from(messages)(m => where(m.id === id.id) select(m)).headOption.map(_.toMessage)
      }
    }

    def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData] = {
      transaction {
        val message = lookupPendingMessage(deliveryTime)
        message.flatMap(updateNextDelivery(_, newNextDelivery))
      }
    }

    private def lookupPendingMessage(deliveryTime: Long) = {
      inTransaction {
        from(messages, queues)((m, q) =>
          where(m.queueName === queueName and
                  queuesToMessagesCond(m, q) and
                  (m.nextDelivery lte deliveryTime)) select(m, q))
                .page(0, 1).headOption.map { case (m, q) => m.toMessage }
      }
    }

    private def updateNextDelivery(message: MessageData, nextDelivery: MillisNextDelivery) = {
      inTransaction {
        val updatedCount = update(messages)(m =>
          where(m.id === message.id.id and m.nextDelivery === message.nextDelivery.millis)
                  set(m.nextDelivery := nextDelivery.millis))

        if (updatedCount == 0) None else Some(message.copy(nextDelivery = nextDelivery))
      }
    }
  }

  def messageStorage(queueName: String) = new SquerylMessageStorage(queueName)
}