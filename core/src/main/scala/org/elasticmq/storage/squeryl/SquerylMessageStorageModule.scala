package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.storage.MessageStorageModule

trait SquerylMessageStorageModule extends MessageStorageModule {
  this: SquerylSchemaModule =>

  object squerylMessageStorage extends MessageStorage {
    def persistMessage(message: SpecifiedMessage) {
      transaction {
        messages.insert(SquerylMessage.from(message))
      }
    }

    def updateVisibilityTimeout(message: SpecifiedMessage, newNextDelivery: MillisNextDelivery) = {
      val originalSquerylMessage = SquerylMessage.from(message)
      val modifiedSquerylMessage = new SquerylMessage(
        originalSquerylMessage.id,
        originalSquerylMessage.queueName,
        originalSquerylMessage.content,
        newNextDelivery.millis,
        originalSquerylMessage.createdTimestamp)

      transaction {
        messages.update(modifiedSquerylMessage)
      }

      modifiedSquerylMessage.toMessage(SquerylQueue.from(message.queue))
    }

    def deleteMessage(message: IdentifiableMessage) {
      transaction {
        val messageId = message.id.get
        messages.delete(messageId)
        messageStatistics.delete(messageId)
      }
    }

    def lookupMessage(id: String) = {
      transaction {
        from(messages, queues)((m, q) => where(m.id === id and queuesToMessagesCond(m, q)) select(m, q))
                .headOption.map { case (m, q) => m.toMessage(q) }
      }
    }

    def receiveMessage(queue: Queue, deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[SpecifiedMessage] = {
      transaction {
        val message = lookupPendingMessage(queue, deliveryTime)
        message.flatMap(updateNextDelivery(_, newNextDelivery))
      }
    }

    private def lookupPendingMessage(queue: Queue, deliveryTime: Long) = {
      inTransaction {
        from(messages, queues)((m, q) =>
          where(m.queueName === queue.name and
                  queuesToMessagesCond(m, q) and
                  (m.nextDelivery lte deliveryTime)) select(m, q))
                .page(0, 1).headOption.map { case (m, q) => m.toMessage(q) }
      }
    }

    private def updateNextDelivery(message: SpecifiedMessage, nextDelivery: MillisNextDelivery) = {
      inTransaction {
        val updatedCount = update(messages)(m =>
          where(m.id === message.id.get and m.nextDelivery === message.nextDelivery.millis)
                  set(m.nextDelivery := nextDelivery.millis))

        if (updatedCount == 0) None else Some(message.copy(nextDelivery = nextDelivery))
      }
    }
  }

  def messageStorage = squerylMessageStorage
}