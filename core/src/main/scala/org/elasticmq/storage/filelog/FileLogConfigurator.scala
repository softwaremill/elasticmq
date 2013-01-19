package org.elasticmq.storage.filelog

import java.util.concurrent.LinkedBlockingQueue
import org.elasticmq.storage._
import com.typesafe.scalalogging.slf4j.Logging

class FileLogConfigurator(delegate: StorageCommandExecutor, configuration: FileLogConfiguration) extends Logging {
  def start(): FileLogStorage = {
    logger.info("Starting file command log, writing logs to %s, rotating after %d commands have been written".format(
      configuration.storageDir, configuration.rotateLogsAfterCommandWritten))

    val rotateChecker = new RotateChecker(configuration)

    // The queue is bounded by the maximum number of commands between rotations. That way the backlog will never be higher
    // than lifetime of a single file.
    val commandQueue = new LinkedBlockingQueue[IdempotentMutativeCommandOrEnd](configuration.rotateLogsAfterCommandWritten)
    val dataDir = new FileLogDataDir(configuration)

    val fileLog = new FileLog(rotateChecker, dataDir, delegate, commandQueue)
    fileLog.restore()

    val fileLogWriter = new FileLogWriter(rotateChecker, dataDir, delegate, commandQueue)
    val fileLogWriterThread = new Thread(fileLogWriter)
    fileLogWriterThread.start()

    new FileLogStorage(delegate, fileLog, fileLogWriterThread)
  }
}
