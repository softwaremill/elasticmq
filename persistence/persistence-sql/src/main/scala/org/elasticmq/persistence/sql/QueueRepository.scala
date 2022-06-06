package org.elasticmq.persistence.sql

import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.util.Logging

class QueueRepository(db: DB) extends Logging {

  import scalikejdbc._
  implicit val session: AutoSession = AutoSession

  private val tableName = SQLSyntax.createUnsafely("queue")

  if (db.persistenceConfig.pruneDataOnInit) {
    logger.debug(s"Deleting stored queues")
    sql"drop table if exists $tableName".execute.apply()
  }

  sql"""
    create table if not exists $tableName (
      name longtext unique,
      data blob
    )""".execute.apply()

  def drop(): Unit = {
    sql"drop table if exists $tableName".execute.apply()
  }

  def findAll(): List[CreateQueueMetadata] = {
    DB localTx { implicit session =>
      sql"select * from $tableName"
        .map(rs => DBQueue(rs))
        .list
        .apply()
        .map(_.toCreateQueue)
    }
  }

  def add(createQueue: CreateQueueMetadata): Int = {
    val db = DBQueue.from(createQueue)
    sql"""insert into $tableName (name, data)
           values (${db.name},
                   ${db.data})""".update.apply
  }

  def update(createQueue: CreateQueueMetadata): Int = {
    val db = DBQueue.from(createQueue)
    sql"""update $tableName set data = ${db.data} where name = ${db.name}""".update.apply
  }

  def remove(queueName: String): Int = {
    sql"delete from $tableName where name = $queueName".update.apply
  }
}
