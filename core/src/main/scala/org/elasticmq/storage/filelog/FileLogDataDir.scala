package org.elasticmq.storage.filelog

import java.io.File
import com.typesafe.scalalogging.slf4j.Logging

private[filelog] class FileLogDataDir(configuration: FileLogConfiguration) extends Logging {
  if (!configuration.storageDir.exists() && !configuration.storageDir.mkdir()) {
    throw new IllegalArgumentException("The directory %s doesn't exist and cannot be created."
      .format(configuration.storageDir.getAbsolutePath))
  }

  private val LogFilePrefix = "log_"
  private val SnapshotFilePrefix = "snapshot_"
  private val FileCounterPadding = 8

  def createOrGetCurrentLogFile(): File = {
    createIfNeeded(allLogFiles.headOption.getOrElse(logFileWithCounter(0)))
  }

  def createOrGetNextLogFile(): File = {
    val currentCounter = counterFromFile(LogFilePrefix, createOrGetCurrentLogFile())
    createIfNeeded(logFileWithCounter(currentCounter + 1))
  }

  def existingLogFilesAfter(snapshotOpt: Option[File]): Seq[File] = {
    val allLogFilesSeq = allLogFiles.toSeq
    snapshotOpt.map(snapshot => {
      val snapshotCounter = counterFromFile(SnapshotFilePrefix, snapshot)
      val firstFileName = dataFileNameWithCounter(LogFilePrefix, snapshotCounter)
      allLogFilesSeq.filter(f => f.getName.compareTo(firstFileName) > 0).reverse
    }).getOrElse(allLogFilesSeq)
  }

  def createOrGetNextSnapshot(): File = {
    val nextCounter = allSnapshotFiles.headOption.map(counterFromFile(SnapshotFilePrefix, _) + 1).getOrElse(0)
    createIfNeeded(new File(configuration.storageDir, dataFileNameWithCounter(SnapshotFilePrefix, nextCounter)))
  }

  def oldestSnapshot: Option[File] = {
    allSnapshotFiles.lastOption
  }

  def deleteSnapshotsExceptOldest() {
    val toDelete = allSnapshotFiles.dropRight(1)
    if (toDelete.size > 0) {
      logger.debug("Deleting (%s) snapshots except the oldest (%s)".format(toDelete, allSnapshotFiles.last))
      toDelete.foreach(_.delete())
    }
  }

  def deleteOldDataFiles() {
    val snapshotsToDelete = allSnapshotFiles.tail
    val logsToDelete = allLogFiles.tail

    logger.debug("Deleting old snapshots (%s) and logs (%s)".format(snapshotsToDelete, logsToDelete))

    // It is important to delete the snapshots first, as otherwise if the system failed with the log file delete and
    // snapshot present, restore would fail as well.
    snapshotsToDelete.foreach(_.delete())
    logsToDelete.foreach(_.delete())
  }

  private def createIfNeeded(file: File): File = {
    if (!file.exists()) {
      file.createNewFile()
    }

    file
  }

  private def logFileWithCounter(counter: Int) = {
    new File(configuration.storageDir, dataFileNameWithCounter(LogFilePrefix, counter))
  }

  private def dataFileNameWithCounter(prefix: String, counter: Int) = {
    prefix + ("%0"+FileCounterPadding+"d").format(counter)
  }

  private def allDataFiles(prefix: String) = {
    configuration.storageDir.listFiles()
      .filter(_.getName.startsWith(prefix))
      // "Bigger" names (larger numbers) come first
      .sortWith((f1, f2) => f1.getName.compareTo(f2.getName) > 0)
  }

  private def allLogFiles = allDataFiles(LogFilePrefix)

  private def allSnapshotFiles = allDataFiles(SnapshotFilePrefix)

  private def counterFromFile(prefix: String, file: File) = {
    file.getName.substring(prefix.length + 1).toInt
  }
}
