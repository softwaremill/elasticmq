package org.elasticmq.persistence.sql

import scalikejdbc.ConnectionPool

object SqlPersistence {

  private var initialized = false

  def initializeSingleton(persistenceConfig: SqlQueuePersistenceConfig): Unit = {
    if (!initialized) {
      Class.forName(persistenceConfig.driverClass)
      ConnectionPool.singleton(persistenceConfig.uri, persistenceConfig.username, persistenceConfig.password)
      initialized = true
    }
  }
}
