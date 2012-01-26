package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq.{MessageId, MessageStatistics}

trait SquerylMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: SquerylSchemaModule =>

  object squerylMessageStatisticsStorage extends MessageStatisticsStorage {
    def readMessageStatistics(messageId: MessageId) = {
      inTransaction {
        messageStatistics
          .lookup(messageId.id)
          .map(_.toMessageStatistics)
          .getOrElse(MessageStatistics.empty)
      }
    }


    def writeMessageStatistics(messageId: MessageId, statistics: MessageStatistics) {
      transaction {
        val messageInDb = messages.lookup(messageId.id)

        if (messageInDb.isDefined) {
          val squerylStatistics = SquerylMessageStatistics.from(messageId, statistics)
          if (statistics.approximateReceiveCount == 1) {
            messageStatistics.insert(squerylStatistics)
          } else {
            messageStatistics.update(squerylStatistics)
          }
        }
      }
    }
  }

  def messageStatisticsStorage = squerylMessageStatisticsStorage
}