package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.storage.QueueStorageModule
import org.squeryl.dsl.ast.BinaryOperatorNodeLogicalBoolean
import org.squeryl.dsl.NumericalExpression

import org.squeryl._
import dsl.ast.BinaryOperatorNodeLogicalBoolean
import dsl.{EnumExpression, StringExpression, Measures, GroupWithMeasures}

trait SquerylQueueStorageModule extends QueueStorageModule {
  this: SquerylSchemaModule =>

  object squerylQueueStorage extends QueueStorage {
    def persistQueue(queue: Queue) {
      transaction {
        queues.insert(SquerylQueue.from(queue))
      }
    }

    def updateQueue(queue: Queue) {
      transaction {
        queues.update(SquerylQueue.from(queue))
      }
    }

    def deleteQueue(queue: Queue) {
      transaction {
        queues.delete(queue.name)
      }
    }

    def lookupQueue(name: String) = {
      transaction {
        queues.lookup(name).map(_.toQueue)
      }
    }

    def listQueues: Seq[Queue] = {
      transaction {
        from(queues)(q => select(q)).map(_.toQueue).toSeq
      }
    }

    def queueStatistics(queue: Queue, deliveryTime: Long): QueueStatistics = {
      transaction {
        def countVisibleMessages() = {
          from(messages)(m =>
            where(m.queueName === queue.name and
                    (m.nextDelivery lte deliveryTime))
                    compute(count(m.id))).single.measures.toLong
        }

        def countInvisibileMessages(): Long = {
          join(messages, messageStatistics.leftOuter)((m, ms) =>
            where(m.queueName === queue.name and
              not (ms.map(_.id) === None) and
              (m.nextDelivery gt deliveryTime))
              compute(count(m.id))
              on(Some(m.id) === ms.map(_.id))
          )
        }

        def countDelayedMessages(): Long = {
          join(messages, messageStatistics.leftOuter)((m, ms) =>
            where(m.queueName === queue.name and
              (ms.map(_.id) === None) and
              (m.nextDelivery gt deliveryTime))
            compute(count(m.id))
            on(Some(m.id) === ms.map(_.id))
          )
        }

        QueueStatistics(queue,
          countVisibleMessages(),
          countInvisibileMessages(),
          countDelayedMessages())
      }
    }
  }

  def queueStorage = squerylQueueStorage
}