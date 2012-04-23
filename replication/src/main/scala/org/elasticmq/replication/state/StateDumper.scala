package org.elasticmq.replication.state

import java.io.{OutputStream, ObjectOutputStream}
import org.elasticmq.data.StateDump
import org.elasticmq.storage.{EndOfCommands, StorageCommandExecutor}

class StateDumper(storageCommandExecutor: StorageCommandExecutor) {
  def dump(outputStream: OutputStream) {
    val oos = new ObjectOutputStream(outputStream)
    storageCommandExecutor.executeStateManagement(dataSource => {
      new StateDump(dataSource).createStream().foreach(oos.writeObject(_))
      oos.writeObject(EndOfCommands)
    })
  }
}
