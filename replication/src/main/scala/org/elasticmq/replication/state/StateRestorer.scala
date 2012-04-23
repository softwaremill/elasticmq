package org.elasticmq.replication.state

import scala.annotation.tailrec
import java.io.{InputStream, ObjectInputStream}
import org.elasticmq.marshalling.ClassLoaderObjectInputStream
import org.elasticmq.storage.{EndOfCommands, ClearStorageCommand, StorageCommandExecutor, IdempotentMutativeCommand}

class StateRestorer(storageCommandExecutor: StorageCommandExecutor) {
  def restore(inputStream: InputStream) {
    val ois = new ClassLoaderObjectInputStream(inputStream)
    storageCommandExecutor.executeStateManagement(dataSource => {
      restore(ois)
    })
  }
  
  private def restore(ois: ObjectInputStream) {
    @tailrec
    def readNext() {
      val nextObject = ois.readObject()
      if (nextObject != EndOfCommands) {
        storageCommandExecutor.execute(nextObject.asInstanceOf[IdempotentMutativeCommand[_]])
        readNext()
      }
    }

    storageCommandExecutor.execute(ClearStorageCommand())
    readNext()
  }
}
