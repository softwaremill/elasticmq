package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.storage.MessageStatisticsStorageModule
import org.elasticmq.{NeverReceived, MessageStatistics, IdentifiableMessage}

trait SquerylMessageStatisticsStorageModule extends MessageStatisticsStorageModule {
  this: SquerylSchemaModule =>

  object squerylMessageStatisticsStorage extends MessageStatisticsStorage {
    def messageReceived(message: IdentifiableMessage, when: Long) {

    }

    def readMessageStatistics(message: IdentifiableMessage) = {
      readMessageStatisticsOption(message)
              .getOrElse(MessageStatistics(message, NeverReceived, 0))
    }

    private def readMessageStatisticsOption(message: IdentifiableMessage): Option[MessageStatistics] = {
      inTransaction {
        messageStatistics
                .lookup(message.id.get)
                .map(_.toMessageStatistics(message))
      }
    }
  }

  def messageStatisticsStorage = squerylMessageStatisticsStorage
}