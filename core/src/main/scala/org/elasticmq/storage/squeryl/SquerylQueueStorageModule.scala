package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.impl.QueueData
import org.elasticmq.storage.QueueStorageModule

import org.squeryl._

trait SquerylQueueStorageModule extends QueueStorageModule {
  this: SquerylSchemaModule =>

  object squerylQueueStorage extends QueueStorage {
    def persistQueue(queue: QueueData) {
      transaction {
        queues.insert(SquerylQueue.from(queue))
      }
    }

    def updateQueue(queue: QueueData) {
      transaction {
        queues.update(SquerylQueue.from(queue))
      }
    }

    def deleteQueue(name: String) {
      transaction {
        queues.delete(name)
      }
    }

    def lookupQueue(name: String) = {
      transaction {
        queues.lookup(name).map(_.toQueue)
      }
    }

    def listQueues: Seq[QueueData] = {
      transaction {
        from(queues)(q => select(q)).map(_.toQueue).toSeq
      }
    }

    def queueStatistics(name: String, deliveryTime: Long): QueueStatistics = {
      transaction {
        def countVisibleMessages() = {
          from(messages)(m =>
            where(m.queueName === name and
                    (m.nextDelivery lte deliveryTime))
                    compute(count(m.id))).single.measures.toLong
        }

        def countInvisibileMessages(): Long = {
          join(messages, messageStatistics.leftOuter)((m, ms) =>
            where(m.queueName === name and
              not (ms.map(_.id) === None) and
              (m.nextDelivery gt deliveryTime))
              compute(count(m.id))
              on(Some(m.id) === ms.map(_.id))
          )
        }

        def countDelayedMessages(): Long = {
          join(messages, messageStatistics.leftOuter)((m, ms) =>
            where(m.queueName === name and
              (ms.map(_.id) === None) and
              (m.nextDelivery gt deliveryTime))
            compute(count(m.id))
            on(Some(m.id) === ms.map(_.id))
          )
        }

        QueueStatistics(
          countVisibleMessages(),
          countInvisibileMessages(),
          countDelayedMessages())
      }
    }
  }

  def queueStorage = squerylQueueStorage
}