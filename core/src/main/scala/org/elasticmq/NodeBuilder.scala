package org.elasticmq

import impl.nativeclient.{NativeHelpersModule, NativeMessageModule, NativeQueueModule, NativeClientModule}
import org.squeryl.internals.DatabaseAdapter
import org.squeryl.adapters.{MySQLAdapter, H2Adapter}
import org.elasticmq.impl.{NowModule, NodeImpl}
import org.elasticmq.impl.scheduler.BackgroundVolatileTaskSchedulerModule
import storage.inmemory.InMemoryStorageModule
import storage.squeryl._
import storage.StorageModule

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

  def withH2InMemoryStorage(inMemoryDatabaseName: String = "elasticmq") = {
    withDatabaseStorage(DBConfiguration(new H2Adapter,
      "jdbc:h2:mem:"+inMemoryDatabaseName+";DB_CLOSE_DELAY=-1",
      "org.h2.Driver",
      None, true, true))
  }

  def withInMemoryStorage() = {
    new InMemoryNodeBuilder()
  }

  def withDatabaseStorage(dbConfiguration: DBConfiguration) = {
    new NodeBuilderWithStorageLifecycle(dbConfiguration)
  }

  trait NodeLogicModules extends NativeClientModule
    with NativeQueueModule
    with NativeMessageModule
    with NativeHelpersModule
    with NowModule
    with BackgroundVolatileTaskSchedulerModule {
    this: StorageModule =>
  }

  class InMemoryNodeBuilder() {
    def build() = {
      val env = new NodeLogicModules with InMemoryStorageModule

      new NodeImpl(env.nativeClient, () => ())
    }
  }

  class NodeBuilderWithStorageLifecycle(dbConfiguration: DBConfiguration) {
    def build() = {
      val env = new NodeLogicModules with SquerylStorageModule

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