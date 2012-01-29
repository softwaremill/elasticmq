package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq.{MessageDoesNotExistException, MessageId, MessageStatistics}

trait SquerylMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
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

    def writeMessageStatistics(messageId: MessageId, statistics: MessageStatistics) {
      writeMessageStatistics(messageId, statistics, true)
    }

    /** @param checkIfMessageExists: Should a check if the message exists be done before writing stats. This should be
     *  {@code false} when we are certain that the message is in the DB.
      */
    def writeMessageStatistics(messageId: MessageId, statistics: MessageStatistics, checkIfMessageExists: Boolean) {
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