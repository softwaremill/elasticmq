package org.elasticmq.storage.filelog

import javax.annotation.concurrent.{ThreadSafe, NotThreadSafe}
import java.io.File
import java.util.concurrent.BlockingQueue
import com.weiglewilczek.slf4s.Logging
import org.elasticmq.storage.{EndOfCommands, StorageCommandExecutor, IdempotentMutativeCommand}

/**
 * The FileLog consists of a series of files, each of which is either a command log, or a snapshot.
 * Incoming commands are appended to the command log. After a specified amount of commands logged (as decided by the
 * `rotateChecker`), a new snapshot is created. When this is done, the old log and the old snapshot may be removed.
 *
 * Hence most of the times you should see two files:
 * * `snapshot_(N-1)`
 * * `log_N`
 *
 * When restoring state the snapshot must be read, and later all commands from the comand log must be applied.
 */
class FileLog(rotateChecker: RotateChecker,
              dataDir: FileLogDataDir,
              storage: StorageCommandExecutor,
              commandQueue: BlockingQueue[IdempotentMutativeCommandOrEnd]) extends Logging {

  @ThreadSafe
  def addCommands(commands: Seq[IdempotentMutativeCommand[_]]) {
    commands.foreach(command => commandQueue.offer(Left(command)))
  }

  @ThreadSafe
  def shutdown() {
    commandQueue.put(Right(EndOfCommands))
  }

  @NotThreadSafe
  def restore() {
    // If there are multiple snapshots, it means there was a crash during last rotation. In any case there should be
    // max 2 snapshot files. If that is the case, only the older one can be considered complete, so using it.
    val oldestSnapshot = dataDir.oldestSnapshot
    oldestSnapshot.foreach(applyCommandsFrom(_))
    dataDir.deleteSnapshotsExceptOldest()

    // In case of a crash, there may be multiple log files. We need to apply all of them after the snapshot was created.
    val commandsInLastLog = dataDir.existingLogFilesAfter(oldestSnapshot).map(applyCommandsFrom(_)).lastOption.getOrElse(0)

    // Initialize the rotate checker with the number of read commands
    rotateChecker.update(commandsInLastLog)

    logger.info("Restore from file log complete")
  }

  /**
   * @return The number of commands that were read.
   */
  private def applyCommandsFrom(file: File): Int = {
    logger.info("Applying commands from (%s)".format(file.getAbsolutePath))

    val reader = CommandReader.create(file)
    var i = 0

    using(reader) {
        reader.readStream().foreach(cmd => {
          storage.executeToleratingAppliedCommands(cmd)
          i += 1
        })
    }

    i
  }
}
