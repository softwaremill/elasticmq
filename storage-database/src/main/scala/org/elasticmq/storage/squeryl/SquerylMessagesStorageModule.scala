package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.data.MessageData
import org.elasticmq.storage.interfaced.MessagesStorage

trait SquerylMessagesStorageModule {
  this: SquerylSchemaModule with SquerylMessageStatisticsStorageModule with SquerylStorageConfigurationModule =>

  class SquerylMessagesStorage(queueName: String) extends MessagesStorage {
    def sendMessage(message: MessageData) {
      inTransaction {
        messages.insert(SquerylMessage.from(queueName, message))
        messageStatisticsStorage(queueName).updateMessageStatistics(message.id, MessageStatistics.empty, false)
      }
    }

    def updateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) {
      inTransaction {
        update(messages)(m =>
          where(m.id === messageId.id)
            set(m.nextDelivery := newNextDelivery.millis))
      }
    }

    def deleteMessage(id: MessageId) {
      inTransaction {
        val messageId = id.id
        messages.delete(messageId)
        messageStatistics.delete(messageId)
      }
    }

    def lookupMessage(id: MessageId) = {
      inTransaction {
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
        val msgs = from(messages, queues)((m, q) =>
          where(m.queueName === queueName and
                  queuesToMessagesCond(m, q) and
                  (m.nextDelivery lte deliveryTime)) select(m, q))
                .page(0, configuration.maxPendingMessageCandidates).map { case (m, q) => m.toMessage }.toList

        // Picking a random message from the available ones. This way we decrease the chance of conflicts
        // in case multiple threads try to receive messages.
        randomMessage(msgs)
      }
    }

    private def randomMessage(msgs: List[MessageData]): Option[MessageData] = {
      msgs match {
        case Nil => None
        case l => {
          val idx = (scala.math.random*l.size).toInt
          Some(l(idx))
        }
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