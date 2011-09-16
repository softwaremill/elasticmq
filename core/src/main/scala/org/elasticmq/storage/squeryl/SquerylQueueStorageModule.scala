package org.elasticmq.storage.squeryl

import org.squeryl.PrimitiveTypeMode._
import org.elasticmq._
import org.elasticmq.storage.QueueStorageModule

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
  }

  def queueStorage = squerylQueueStorage
}