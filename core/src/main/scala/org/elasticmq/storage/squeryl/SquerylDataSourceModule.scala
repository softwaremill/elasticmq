package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.storage._
import org.elasticmq.MessageId
import org.elasticmq.data.{DataSource, QueueData}

trait SquerylDataSourceModule {
  this: SquerylSchemaModule =>

  class SquerylDataSource extends DataSource {
    def queuesData = {
      from(queues)(q => select(q)).map(_.toQueue)
    }

    def messagesData(queue: QueueData) = {
      from(messages)(m => where(m.queueName === queue.name) select(m)).map(_.toMessage)
    }

    def messageStatisticsWithId(queue: QueueData) = {
      from(messageStatistics)(ms => select(ms)).map(sms => (MessageId(sms.id), sms.toMessageStatistics))
    }
  }

  def executeWithDataSource[T](f: (DataSource) => T) = {
    transaction {
      f(new SquerylDataSource)
    }
  }
}
