package org.elasticmq.storage.filelog

import java.util.concurrent.LinkedBlockingQueue
import org.elasticmq.storage._
import com.weiglewilczek.slf4s.Logging

class FileLogConfigurator(delegate: StorageCommandExecutor, configuration: FileLogConfiguration) extends Logging {
  def start(): FileLogStorage = {
    logger.info("Starting file command log, writing logs to %s, rotating after %d commands have been written".format(
      configuration.storageDir, configuration.rotateLogsAfterCommandWritten))

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
