package org.elasticmq

import org.squeryl.internals.DatabaseAdapter
import org.squeryl.adapters.{MySQLAdapter, H2Adapter}
import org.elasticmq.impl.{NowModule, NativeClientModule, NodeImpl}
import org.elasticmq.impl.scheduler.BackgroundVolatileTaskSchedulerModule
import storage.squeryl._

object NodeBuilder {
  def withMySQLStorage(dbName: String, username: String, password: String,
                       host: String = "localhost", port: Int = 3306,
                       create: Boolean = true,
                       drop: Boolean = false) = {
    withDatabaseStorage(DBConfiguration(new MySQLAdapter,
      "jdbc:mysql://"+host+":"+port+"/"+dbName+"?useUnicode=true&amp;characterEncoding=UTF-8&amp;cacheServerConfiguration=true",
      "com.mysql.jdbc.Driver",
      Some(username, password),
      create, drop))
  }

  def withInMemoryStorage(inMemoryDatabaseName: String = "elasticmq") = {
    withDatabaseStorage(DBConfiguration(new H2Adapter,
      "jdbc:h2:mem:"+inMemoryDatabaseName+";DB_CLOSE_DELAY=-1",
      "org.h2.Driver",
      None, true, true))
  }

  def withDatabaseStorage(dbConfiguration: DBConfiguration) = {
    new NodeBuilderWithStorageLifecycle(dbConfiguration)
  }

  class NodeBuilderWithStorageLifecycle(dbConfiguration: DBConfiguration) {
    def build() = {
      val env = new NativeClientModule
              with SquerylStorageModule
              with NowModule
              with BackgroundVolatileTaskSchedulerModule

      env.initializeSqueryl(dbConfiguration)
      new NodeImpl(env.nativeClient, () => env.shutdownSqueryl(dbConfiguration.drop))
    }
  }
}

case class DBConfiguration(dbAdapter: DatabaseAdapter,
                           jdbcURL: String,
                           driverClass: String,
                           credentials: Option[(String, String)] = None,
                           create: Boolean = true,
                           drop: Boolean = true)