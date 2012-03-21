package org.elasticmq.replication.state

import scala.annotation.tailrec
import org.elasticmq.storage.{ClearStorageCommand, StorageCommandExecutor, IdempotentMutativeCommand}
import java.io.{InputStream, ObjectInputStream}

class StateRestorer(storageCommandExecutor: StorageCommandExecutor) {
  def restore(inputStream: InputStream) {
    val ois = new ObjectInputStream(inputStream)
    // TODO ...
    storageCommandExecutor.executeWithDataSource(dataSource => {
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
