package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.{MessageDoesNotExistException, MessageId, MessageStatistics}
import org.elasticmq.storage.interfaced.MessageStatisticsStorage

trait SquerylMessageStatisticsStorageModule {
  this: SquerylSchemaModule =>

  class SquerylMessageStatisticsStorage(queueName: String) extends MessageStatisticsStorage {
    def readMessageStatistics(messageId: MessageId) = {
      inTransaction {
        messageStatistics
          .lookup(messageId.id)
          .map(_.toMessageStatistics)
          .getOrElse(throw new MessageDoesNotExistException(queueName, messageId))
      }
    }

    def updateMessageStatistics(messageId: MessageId, statistics: MessageStatistics) {
      updateMessageStatistics(messageId, statistics, true)
    }

    /**
     * @param checkIfMessageExists: Should a check if the message exists be done before writing stats. This should be
     * `false` when we are certain that the message is in the DB.
     */
    def updateMessageStatistics(messageId: MessageId, statistics: MessageStatistics, checkIfMessageExists: Boolean) {
      inTransaction {
        lazy val messageInDb = messages.lookup(messageId.id)

        if (!checkIfMessageExists || messageInDb.isDefined) {
          val squerylStatistics = SquerylMessageStatistics.from(messageId, statistics)
          if (statistics.approximateReceiveCount == 0) {
            messageStatistics.insert(squerylStatistics)
          } else {
            messageStatistics.update(squerylStatistics)
          }
        }
      }
    }
  }

  def messageStatisticsStorage(queueName: String) = new SquerylMessageStatisticsStorage(queueName)
}