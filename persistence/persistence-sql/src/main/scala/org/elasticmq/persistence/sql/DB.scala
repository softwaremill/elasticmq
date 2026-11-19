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
      ConnectionPool.singleton(
        withWriteDelay(persistenceConfig.uri),
        persistenceConfig.username,
        persistenceConfig.password
      )
      initialized = true
    }
  }

  /** By default H2 keeps committed changes in memory and writes them to the database file later, in the background. A
    * process that is killed before then (e.g. `kill -9`, an OOM kill or `docker kill`) loses messages whose send was
    * already acknowledged. `WRITE_DELAY=0` writes each commit to the file before the request is answered. It doesn't
    * force the file to disk, so an operating system crash or power loss can still lose recent changes.
    *
    * A `WRITE_DELAY` set in the URI is kept.
    */
  private[sql] def withWriteDelay(uri: String): String =
    if (uri.startsWith("jdbc:h2:") && !uri.toUpperCase.contains(";WRITE_DELAY=")) uri + ";WRITE_DELAY=0"
    else uri
}
