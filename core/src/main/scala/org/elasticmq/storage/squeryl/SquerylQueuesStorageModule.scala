package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.data.QueueData

import org.squeryl._
import org.elasticmq.storage.interfaced.QueuesStorage

trait SquerylQueuesStorageModule {
  this: SquerylSchemaModule =>

  class SquerylQueuesStorage extends QueuesStorage {
    def createQueue(queue: QueueData) {
      def isUniqueIndexException(e: Exception) = {
        val msg = e.getMessage.toLowerCase
        msg.contains("unique index") || msg.contains("unique key") || msg.contains("primary key")
      }

      transaction {
        try {
          queues.insert(SquerylQueue.from(queue))
        } catch {
          case e: RuntimeException if isUniqueIndexException(e) =>
            throw new QueueAlreadyExistsException(queue.name)
        }
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
          join(messages, messageStatistics)((m, ms) =>
            where((m.queueName === name) and
              (ms.approximateReceiveCount gt 0) and
              (m.nextDelivery gt deliveryTime))
              compute(count(m.id))
              on(m.id === ms.id)
          )
        }

        def countDelayedMessages(): Long = {
          join(messages, messageStatistics)((m, ms) =>
            where((m.queueName === name) and
              (ms.approximateReceiveCount === 0) and
              (m.nextDelivery gt deliveryTime))
            compute(count(m.id))
            on(m.id === ms.id)
          )
        }

        QueueStatistics(
          countVisibleMessages(),
          countInvisibileMessages(),
          countDelayedMessages())
      }
    }
  }

  val queuesStorage = new SquerylQueuesStorage
}