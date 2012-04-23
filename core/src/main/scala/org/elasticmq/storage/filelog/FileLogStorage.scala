package org.elasticmq.storage.filelog

import org.elasticmq.data.DataSource
import org.elasticmq.storage.{StorageCommand, StorageCommandExecutor}

class FileLogStorage(delegate: StorageCommandExecutor,
                     fileLog: FileLog,
                     fileLogWriterThread: Thread) extends StorageCommandExecutor {

  def execute[R](command: StorageCommand[R]) = {
    val result = delegate.execute(command)

    fileLog.addCommands(command.resultingMutations(result))

    result
  }

  def executeStateManagement[T](f: (DataSource) => T) = delegate.executeStateManagement(f)

  def shutdown() {
    fileLog.shutdown()
    fileLogWriterThread.join()
    delegate.shutdown()
  }
}

