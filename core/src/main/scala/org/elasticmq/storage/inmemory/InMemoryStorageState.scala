package org.elasticmq.storage.inmemory

import org.elasticmq.storage.interfaced.StorageState
import java.io.{ObjectInputStream, ObjectOutputStream, OutputStream, InputStream}
import scala.collection.mutable.ConcurrentMap

class InMemoryStorageState(inMemoryQueuesStorage: InMemoryQueuesStorage) extends StorageState {
  def dump(outputStream: OutputStream) {
    val oos = new ObjectOutputStream(outputStream)
    oos.writeObject(inMemoryQueuesStorage.queues)
  }

  def restore(inputStream: InputStream) {
    val ois = new ObjectInputStream(inputStream)
    inMemoryQueuesStorage.replaceWithQueues(ois.readObject().asInstanceOf[ConcurrentMap[String, InMemoryQueue]])
  }
}
