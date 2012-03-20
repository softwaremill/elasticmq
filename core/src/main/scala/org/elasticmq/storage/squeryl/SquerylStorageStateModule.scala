package org.elasticmq.storage.squeryl

import java.io.{ObjectOutputStream, ObjectInputStream, OutputStream, InputStream}
import org.elasticmq.storage.interfaced.StorageStateManager
import org.squeryl.PrimitiveTypeMode._
import org.elasticmq.data.QueueData
import org.elasticmq.storage._
import org.elasticmq.MessageId
import org.elasticmq.storage.statemanager.command.{StateDumper, MQDataSource, StateRestorer}

trait SquerylStorageStateModule {
  this: SquerylSchemaModule =>
  
  class SquerylStorageStateManager(storageCommandExecutor: StorageCommandExecutor) extends StorageStateManager {
    def dump(outputStream: OutputStream) {
      val oos = new ObjectOutputStream(outputStream)

      transaction {
        new StateDumper(new SquerylMQDataSource, oos).dumpQueues()
      }
    }

    def restore(inputStream: InputStream) {
      val ois = new ObjectInputStream(inputStream)

      transaction {
        clearStorage()
        new StateRestorer(storageCommandExecutor).restore(ois)
      }
    }

    private def clearStorage() {
      deleteAll(messageStatistics)
      deleteAll(messages)
      deleteAll(queues)
    }
  }

  class SquerylMQDataSource extends MQDataSource {
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

  def storageState(storageCommandExecutor: StorageCommandExecutor) = new SquerylStorageStateManager(storageCommandExecutor)
}
