package org.elasticmq.persistence.sql
import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.persistence.sql.CreateQueueProtocol._
import scalikejdbc.WrappedResultSet
import spray.json._

case class DBQueue(name: String, data: String) {
  def toCreateQueue: CreateQueueMetadata = {
    data.parseJson.convertTo[CreateQueueMetadata]
  }
}

object DBQueue {
  def apply(rs: WrappedResultSet) = new DBQueue(
    rs.string("name"),
    rs.string("data")
  )

  def from(createQueue: CreateQueueMetadata): DBQueue = {
    DBQueue(createQueue.name, createQueue.toJson.toString)
  }
}
