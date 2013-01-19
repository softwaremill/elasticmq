package org.elasticmq.storage.filelog

import javax.annotation.concurrent.NotThreadSafe
import collection.JavaConverters._
import org.elasticmq.storage.{StorageCommandExecutor, IdempotentMutativeCommand}
import org.elasticmq.data.StateDump
import java.util.concurrent.BlockingQueue
import java.util.ArrayList
import com.typesafe.scalalogging.slf4j.Logging
import annotation.tailrec

@NotThreadSafe
class FileLogWriter(rotateChecker: RotateChecker,
                    dataDir: FileLogDataDir,
                    storage: StorageCommandExecutor,
                    commandQueue: BlockingQueue[IdempotentMutativeCommandOrEnd]) extends Runnable with Logging {

  private var currentCommandWriter = CommandWriter.create(dataDir.createOrGetCurrentLogFile())

  def run() {
    doRun()

    currentCommandWriter.close()

    logger.info("File log writer thread exiting")
  }

  @tailrec
  private def doRun() {
    commandQueue.take match {
      case Left(command) => {
        logCommands(command :: Nil)
        doRun()
      }
      case Right(_) =>
    }
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
