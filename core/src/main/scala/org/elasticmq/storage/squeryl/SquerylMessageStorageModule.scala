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

    def updateMessage(message: SpecifiedMessage) {
      transaction {
        messages.update(SquerylMessage.from(message))
      }
    }

    def updateNextDelivery(message: SpecifiedMessage, nextDelivery: MillisNextDelivery) = {
      transaction {
        val updatedCount = update(messages)(m =>
          where(m.id === message.id and m.nextDelivery === message.nextDelivery.millis)
                  set(m.nextDelivery := nextDelivery.millis))

        if (updatedCount == 0) None else Some(message.copy(nextDelivery = nextDelivery))
      }
    }

    def deleteMessage(message: AnyMessage) {
      transaction {
        messages.delete(message.id)
      }
    }

    def lookupMessage(id: String) = {
      transaction {
        from(messages, queues)((m, q) => where(m.id === id and queuesToMessagesCond(m, q)) select(m, q))
                .headOption.map { case (m, q) => m.toMessage(q) }
      }
    }

    def lookupPendingMessage(queue: Queue, deliveryTime: Long) = {
      transaction {
        from(messages, queues)((m, q) =>
          where(m.queueName === queue.name and
                  queuesToMessagesCond(m, q) and
                  (m.nextDelivery lte deliveryTime)) select(m, q))
                .page(0, 1).headOption.map { case (m, q) => m.toMessage(q) }
      }
    }
  }

  def messageStorage = squerylMessageStorage
}