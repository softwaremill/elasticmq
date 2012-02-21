package org.elasticmq.storage.squeryl

import org.elasticmq.storage.interfaced.InterfacedCommandExecutor

class SquerylStorageCommandExecutor extends InterfacedCommandExecutor {
  val modules =
    new SquerylInitializerModule
      with SquerylSchemaModule
      with SquerylQueuesStorageModule
      with SquerylMessagesStorageModule
      with SquerylMessageStatisticsStorageModule


  def queuesStorage = modules.queuesStorage
  def messagesStorage(queueName: String) = modules.messagesStorage(queueName)
  def messageStatisticsStorage(queueName: String) = modules.messageStatisticsStorage(queueName)
}
