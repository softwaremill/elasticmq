package org.elasticmq.storage.inmemory

import org.elasticmq.storage.interfaced.StorageStateManager
import java.io.{ObjectInputStream, ObjectOutputStream, OutputStream, InputStream}
import org.elasticmq.data.QueueData
import org.elasticmq.storage.statemanager.command.{StateRestorer, StateDumper, MQDataSource}
import org.elasticmq.storage.StorageCommandExecutor

class InMemoryStorageStateManager(inMemoryQueuesStorage: InMemoryQueuesStorage,
                                  storageCommandExecutor: StorageCommandExecutor) extends StorageStateManager {

  def dump(outputStream: OutputStream) {
    val oos = new ObjectOutputStream(outputStream)
    new StateDumper(new InMemoryMQDataSource, oos).dumpQueues()
  }

  def restore(inputStream: InputStream) {
    val ois = new ObjectInputStream(inputStream)
    inMemoryQueuesStorage.queues.clear()
    new StateRestorer(storageCommandExecutor).restore(ois)
  }

  class InMemoryMQDataSource() extends MQDataSource {
    def queuesData = {
      inMemoryQueuesStorage.queues.values.map(_.queueData)
    }

    def messagesData(queue: QueueData) = {
      inMemoryQueuesStorage(queue.name).messages.messagesById.values.map(_.toMessageData)
    }

    def messageStatisticsWithId(queue: QueueData) = {
      inMemoryQueuesStorage(queue.name).statistics.statistics
    }
  }
}
