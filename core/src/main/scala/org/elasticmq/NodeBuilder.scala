package org.elasticmq

import impl.NodeImpl
import storage.squeryl.SquerylStorage
import storage.Storage
import org.squeryl.internals.DatabaseAdapter
import org.squeryl.adapters.{MySQLAdapter, H2Adapter}

object NodeBuilder {
  private def squerylShutdown(drop: Boolean) = () => SquerylStorage.shutdown(drop)

  def withMySQLStorage(dbName: String, username: String, password: String,
                       host: String = "localhost", port: Int = 3306,
                       create: Boolean = true,
                       drop: Boolean = false) = {
    withDatabaseStorage(new MySQLAdapter,
      "jdbc:mysql://"+host+":"+port+"/"+dbName+"?useUnicode=true&amp;characterEncoding=UTF-8",
      "com.mysql.jdbc.Driver",
      Some(username, password),
      create, drop)
  }

  def withInMemoryStorage(inMemoryDatabaseName: String = "elasticmq") = {
    withDatabaseStorage(new H2Adapter,
      "jdbc:h2:mem:"+inMemoryDatabaseName+";DB_CLOSE_DELAY=-1",
      "org.h2.Driver",
      None, true, true)
  }

  def withDatabaseStorage(dbAdapter: DatabaseAdapter, jdbcURL: String, driverClass: String,
                          credentials: Option[(String, String)],
                          create: Boolean, drop: Boolean) = {
    new NodeBuilderWithStorageLifecycle(new SquerylStorage(), () => {
      SquerylStorage.initialize(dbAdapter, jdbcURL, driverClass, credentials, create)
    }, squerylShutdown(drop))
  }

  class NodeBuilderWithStorageLifecycle(storage: Storage, startup: () => Unit, shutdown: () => Unit) {
    def build() = {
      startup()
      new NodeImpl(storage, shutdown)
    }
  }
}