package org.elasticmq.storage.squeryl

import org.elasticmq.storage.interfaced.InterfacedCommandExecutor
import org.elasticmq.data.DataSource

class SquerylStorage(dbConfiguration: DBConfiguration) extends InterfacedCommandExecutor {
  val modules =
    new SquerylInitializerModule
      with SquerylSchemaModule
      with SquerylQueuesStorageModule
      with SquerylMessagesStorageModule
      with SquerylMessageStatisticsStorageModule
      with SquerylStorageConfigurationModule
      with SquerylDataSourceModule

  modules.initializeSqueryl(dbConfiguration)

  def queuesStorage = modules.queuesStorage
  def messagesStorage(queueName: String) = modules.messagesStorage(queueName)
  def messageStatisticsStorage(queueName: String) = modules.messageStatisticsStorage(queueName)

  def executeStateManagement[T](f: (DataSource) => T) = modules.executeStateManagement(f)

  def shutdown() {
    modules.shutdownSqueryl(dbConfiguration.drop)
  }
}
