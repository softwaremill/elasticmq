package org.elasticmq.storage.filelog

import java.util.concurrent.LinkedBlockingQueue
import org.elasticmq.storage._

class FileLogConfigurator(delegate: StorageCommandExecutor, configuration: FileLogConfiguration) {
  def start(): FileLogStorage = {
    val rotateChecker = new RotateChecker(configuration)
    val commandQueue = new LinkedBlockingQueue[IdempotentMutativeCommandOrEnd]
    val dataDir = new FileLogDataDir(configuration)

    val fileLog = new FileLog(rotateChecker, dataDir, delegate, commandQueue)
    fileLog.restore()

    val fileLogWriter = new FileLogWriter(rotateChecker, dataDir, delegate, commandQueue)
    val fileLogWriterThread = new Thread(fileLogWriter)
    fileLogWriterThread.start()

    new FileLogStorage(delegate, fileLog, fileLogWriterThread)
  }
}
