package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.data.MessageData
import org.elasticmq.storage.interfaced.MessagesStorage

trait SquerylMessagesStorageModule {
  this: SquerylSchemaModule with SquerylMessageStatisticsStorageModule =>

  class SquerylMessagesStorage(queueName: String) extends MessagesStorage {
    def sendMessage(message: MessageData) {
      transaction {
        messages.insert(SquerylMessage.from(queueName, message))
        messageStatisticsStorage(queueName).updateMessageStatistics(message.id, MessageStatistics.empty, false)
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
      inTransaction {
        val messageOption = lookupPendingMessage(deliveryTime)
        messageOption.flatMap(message => {
          // The message may already have been received by another thread. In that case, trying again.
          updateNextDelivery(message, newNextDelivery) match {
            case None => receiveMessage(deliveryTime, newNextDelivery)
            case some => some
          }
        })
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

  def messagesStorage(queueName: String) = new SquerylMessagesStorage(queueName)
}