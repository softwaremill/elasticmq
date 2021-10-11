package org.elasticmq.persistence.sql

import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import org.elasticmq.util.Logging
import scalikejdbc._
import spray.json._


class QueueRepository(persistenceConfig: SqlQueuePersistenceConfig) extends Logging {

  implicit val session: AutoSession = AutoSession

  SqlPersistence.initializeSingleton(persistenceConfig)

  private val tableName = SQLSyntax.createUnsafely("queue")

  if (persistenceConfig.pruneDataOnInit) {
    logger.debug(s"Deleting stored queues")
    sql"drop table if exists $tableName".execute.apply()
  }

  sql"""
    create table if not exists $tableName (
      name varchar unique,
      data blob
    )""".execute.apply()

  object DeadLettersQueueProtocol extends DefaultJsonProtocol {
    implicit val DeadLettersQueueFormat: JsonFormat[DeadLettersQueue] = jsonFormat2(DeadLettersQueue)
  }

  import DeadLettersQueueProtocol._

  object CreateQueueProtocol extends DefaultJsonProtocol {
    implicit val CreateQueueFormat: JsonFormat[CreateQueueMetadata] = jsonFormat10(CreateQueueMetadata.apply)
  }

  import CreateQueueProtocol._

  case class DBQueue(name: String, data: String) {

    def toCreateQueue: CreateQueueMetadata = {
      data.parseJson.convertTo[CreateQueueMetadata]
    }
  }

  object DBQueue extends SQLSyntaxSupport[DBQueue] {
    override val tableName = "queue"

    def apply(rs: WrappedResultSet) = new DBQueue(
      rs.string("name"),
      rs.string("data")
    )

    def from(createQueue: CreateQueueMetadata): DBQueue = {
      DBQueue(createQueue.name, createQueue.toJson.toString)
    }
  }

  def drop(): Unit = {
    sql"drop table if exists $tableName".execute.apply()
  }

  def findAll(): List[CreateQueueMetadata] = {
    DB localTx { implicit session =>
      sql"select * from $tableName"
        .map(rs => DBQueue(rs)).list.apply()
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
