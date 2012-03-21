package org.elasticmq.storage.squeryl

import org.elasticmq.storage.interfaced.InterfacedCommandExecutor
import org.elasticmq.data.DataSource

class SquerylStorageCommandExecutor extends InterfacedCommandExecutor {
  val modules =
    new SquerylInitializerModule
      with SquerylSchemaModule
      with SquerylQueuesStorageModule
      with SquerylMessagesStorageModule
      with SquerylMessageStatisticsStorageModule
      with SquerylStorageConfigurationModule
      with SquerylDataSourceModule

  def queuesStorage = modules.queuesStorage
  def messagesStorage(queueName: String) = modules.messagesStorage(queueName)
  def messageStatisticsStorage(queueName: String) = modules.messageStatisticsStorage(queueName)

  def executeWithDataSource[T](f: (DataSource) => T) = modules.executeWithDataSource(f)
}
