package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq.{NeverReceived, MessageStatistics, IdentifiableMessage}

trait SquerylMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: SquerylSchemaModule =>

  object squerylMessageStatisticsStorage extends MessageStatisticsStorage {
    def messageReceived(message: IdentifiableMessage, when: Long) {
      transaction {
        val current = lookupStatistics(message)
        current match {
          case None => messageStatistics.insert(new SquerylMessageStatistics(message.id.get, when, 1))
          case Some(stats) => messageStatistics.update(new SquerylMessageStatistics(
            stats.id, stats.approximateFirstReceive, stats.approximateReceiveCount+1
          ))
        }
      }
    }

    def readMessageStatistics(message: IdentifiableMessage) = {
      lookupStatistics(message)
              .map(_.toMessageStatistics(message))
              .getOrElse(MessageStatistics(message, NeverReceived, 0))
    }


    def writeMessageStatistics(statistics: MessageStatistics) {
      transaction {
        val squerylStatistics = SquerylMessageStatistics.from(statistics)
        if (statistics.approximateReceiveCount == 1) {
          messageStatistics.insert(squerylStatistics)
        } else {
          messageStatistics.update(squerylStatistics)
        }
      }
    }

    private def lookupStatistics(message: IdentifiableMessage): Option[SquerylMessageStatistics] = {
      inTransaction {
        messageStatistics.lookup(message.id.get)
      }
    }
  }

  def messageStatisticsStorage = squerylMessageStatisticsStorage
}