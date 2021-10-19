package org.elasticmq.persistence.sql
import scalikejdbc.ConnectionPool

class DB(val persistenceConfig: SqlQueuePersistenceConfig) {
  DB.initializeSingleton(persistenceConfig)
}

object DB {
  private var initialized = false

  def initializeSingleton(persistenceConfig: SqlQueuePersistenceConfig): Unit = {
    if (!initialized) {
      Class.forName(persistenceConfig.driverClass)
      ConnectionPool.singleton(persistenceConfig.uri, persistenceConfig.username, persistenceConfig.password)
      initialized = true
    }
  }
}
