package org.elasticmq

import impl.NodeImpl
import storage.squeryl.SquerylStorage
import storage.Storage
import org.squeryl.internals.DatabaseAdapter
import org.squeryl.adapters.{MySQLAdapter, H2Adapter}

object NodeBuilder {
  private val squerylShutdown = () => SquerylStorage.shutdown()

  def withMysqlStorage(dbName: String, host: String = "localhost", port: Int = 3306) = {
    withDatabaseStorage(new MySQLAdapter,
      "jdbc:mysql://"+host+":"+port+"/"+dbName+"?useUnicode=true&amp;characterEncoding=UTF-8",
      "com.mysql.jdbc.Driver")
  }

  def withInMemoryStorage(inMemoryDatabaseName: String = "elasticmq") = {
    withDatabaseStorage(new H2Adapter, "jdbc:h2:mem:"+inMemoryDatabaseName+";DB_CLOSE_DELAY=-1", "org.h2.Driver")
  }

  def withDatabaseStorage(dbAdapter: DatabaseAdapter, jdbcURL: String, dbDriverName: String) = {
    new NodeBuilderWithStorageLifecycle(new SquerylStorage(), () => {
      Thread.currentThread().getContextClassLoader.loadClass(dbDriverName);
      SquerylStorage.initialize(dbAdapter, jdbcURL)
    }, squerylShutdown)
  }

  class NodeBuilderWithStorageLifecycle(storage: Storage, startup: () => Unit, shutdown: () => Unit) {
    def build() = {
      startup()
      new NodeImpl(storage, shutdown)
    }
  }
}