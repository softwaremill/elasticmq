package org.elasticmq.storage.squeryl

import org.squeryl.internals.DatabaseAdapter
import org.squeryl.adapters.{H2Adapter, MySQLAdapter}

case class DBConfiguration(dbAdapter: DatabaseAdapter,
                           jdbcURL: String,
                           driverClass: String,
                           credentials: Option[(String, String)] = None,
                           create: Boolean = true,
                           drop: Boolean = true)

object DBConfiguration {
  def mysql(dbName: String, username: String, password: String,
            host: String = "localhost", port: Int = 3306,
            create: Boolean = true,
            drop: Boolean = false) = {
    DBConfiguration(new MySQLAdapter,
      "jdbc:mysql://"+host+":"+port+"/"+dbName+"?useUnicode=true&amp;characterEncoding=UTF-8&amp;cacheServerConfiguration=true",
      "com.mysql.jdbc.Driver",
      Some(username, password),
      create, drop)
  }

  def h2(inMemoryDatabaseName: String = "elasticmq") = {
    DBConfiguration(new H2Adapter,
      "jdbc:h2:mem:"+inMemoryDatabaseName+";DB_CLOSE_DELAY=-1",
      "org.h2.Driver",
      None, true, true)
  }
}
