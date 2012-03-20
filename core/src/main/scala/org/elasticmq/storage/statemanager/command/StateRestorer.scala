package org.elasticmq.storage.statemanager.command

import java.io.ObjectInputStream
import scala.annotation.tailrec
import org.elasticmq.storage.{StorageCommandExecutor, IdempotentMutativeCommand}

class StateRestorer(storageCommandExecutor: StorageCommandExecutor) {
  def restore(ois: ObjectInputStream) {
    @tailrec
    def readNext() {
      val nextObject = ois.readObject()
      if (nextObject != EndOfCommands) {
        storageCommandExecutor.execute(nextObject.asInstanceOf[IdempotentMutativeCommand[_]])
        readNext()
      }
    }
    
    readNext()
  }
}
