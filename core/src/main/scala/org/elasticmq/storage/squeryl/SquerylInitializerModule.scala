package org.elasticmq.storage.squeryl

import org.squeryl._
import PrimitiveTypeMode._
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.elasticmq.DBConfiguration

trait SquerylInitializerModule {
  this: SquerylSchemaModule =>

  def initializeSqueryl(dbConfiguration: DBConfiguration) {
    import org.squeryl.SessionFactory

    val cpds = new ComboPooledDataSource
    cpds.setDriverClass(dbConfiguration.driverClass)
    cpds.setJdbcUrl(dbConfiguration.jdbcURL)

    dbConfiguration.credentials match {
      case Some((username, password)) => {
        cpds.setUser(username)
        cpds.setPassword(password)
      }
      case _ =>
    }

    SessionFactory.concreteFactory = Some(() => Session.create(cpds.getConnection, dbConfiguration.dbAdapter))

    if (dbConfiguration.create) {
      transaction {
        try {
          MQSchema.create
        } catch {
          case e: Exception if e.getMessage.contains("already exists") => // do nothing
          case e => throw e
        }
      }
    }
  }

  def shutdownSqueryl(drop: Boolean) {
    if (drop) {
      transaction {
        MQSchema.drop
      }
    }
  }

}