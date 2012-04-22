package org.elasticmq.storage.filelog

import javax.annotation.concurrent.NotThreadSafe
import java.util.concurrent.atomic.AtomicBoolean
import collection.JavaConverters._
import org.elasticmq.storage.{StorageCommandExecutor, IdempotentMutativeCommand}
import org.elasticmq.data.StateDump
import java.util.concurrent.BlockingQueue
import java.util.ArrayList
import com.weiglewilczek.slf4s.Logging

@NotThreadSafe
class FileLogWriter(rotateChecker: RotateChecker,
                    dataDir: FileLogDataDir,
                    storage: StorageCommandExecutor,
                    running: AtomicBoolean,
                    commandQueue: BlockingQueue[IdempotentMutativeCommand[_]]) extends Runnable with Logging {

  private val MaxBatch = 500
  private val drainList = new ArrayList[IdempotentMutativeCommand[_]](MaxBatch)
  private var currentCommandWriter = CommandWriter.create(dataDir.createOrGetCurrentLogFile())

  def run() {
    while (running.get()) {
      commandQueue.drainTo(drainList, MaxBatch)
      if (drainList.size() != 0) {
        logCommands(drainList.asScala)
        drainList.clear()
      }
    }

    currentCommandWriter.close()
  }

  private def logCommands(commands: Seq[IdempotentMutativeCommand[_]]) {
    commands.foreach(currentCommandWriter.write(_))

    if (rotateChecker.shouldRotate(commands.size)) {
      rotate()
      rotateChecker.reset()
    }
  }

  def rotate() {
    logger.info("Command log full, rotating")

    dumpCurrentStateToNewSnapshot()

    // Then writing commands to a new log
    currentCommandWriter.close()

    val nextLogFile = dataDir.createOrGetNextLogFile()
    logger.info("Writing logs to file (%s)".format(nextLogFile.getAbsolutePath))

    currentCommandWriter = CommandWriter.create(nextLogFile)

    // Now it's possible to safely remove the old logs
    dataDir.deleteOldDataFiles()
  }

  private def dumpCurrentStateToNewSnapshot() {
    val nextSnapshot = dataDir.createOrGetNextSnapshot()
    logger.info("Writing snapshot to (%s)".format(nextSnapshot.getAbsolutePath))

    val snapshotWriter = CommandWriter.create(nextSnapshot)
    using(snapshotWriter) {
      storage.executeStateManagement(dataSource => {
        new StateDump(dataSource).createStream().foreach(snapshotWriter.write(_))
      })
    }
  }
}
