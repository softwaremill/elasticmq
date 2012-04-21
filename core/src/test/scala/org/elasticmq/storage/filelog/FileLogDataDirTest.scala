package org.elasticmq.storage.filelog

import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.elasticmq.test._

class FileLogDataDirTest extends FunSuite with MustMatchers with BeforeAndAfter {
  var configuration: FileLogConfiguration = _
  var fileLogDataDir: FileLogDataDir = _

  before {
    configuration = FileLogConfiguration(createTempDir(), 0)
    fileLogDataDir = new FileLogDataDir(configuration)
  }

  after {
    deleteDirRecursively(configuration.storageDir)
  }

  test("initial current log when no other files are present must be all-0") {
    fileLogDataDir.createOrGetCurrentLogFile().getName must be ("log_00000000")
  }

  test("current log file when other log files are present must be the latest") {
    fileLogDataDir.createOrGetCurrentLogFile()
    fileLogDataDir.createOrGetNextLogFile()

    val log1 = fileLogDataDir.createOrGetCurrentLogFile()
    fileLogDataDir.createOrGetNextLogFile()

    val log2 = fileLogDataDir.createOrGetCurrentLogFile()

    log1.getName must be ("log_00000001")
    log2.getName must be ("log_00000002")
  }

  test("next log file must have a number greater than the latest") {
    fileLogDataDir.createOrGetCurrentLogFile()

    val next1 = fileLogDataDir.createOrGetNextLogFile()
    val next2 = fileLogDataDir.createOrGetNextLogFile()

    next1.getName must be ("log_00000001")
    next2.getName must be ("log_00000002")
  }

  test("next snapshot initially must be all-0") {
    fileLogDataDir.createOrGetNextSnapshot().getName must be ("snapshot_00000000")
  }

  test("next snapshot file must have a number greater than the latest") {
    fileLogDataDir.createOrGetNextSnapshot()

    val snapshot1 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot2 = fileLogDataDir.createOrGetNextSnapshot()

    snapshot1.getName must be ("snapshot_00000001")
    snapshot2.getName must be ("snapshot_00000002")
  }

  test("existingLogFilesAfter") {
    // Given
    fileLogDataDir.createOrGetNextSnapshot()
    val snapshot1 = fileLogDataDir.createOrGetNextSnapshot()

    fileLogDataDir.createOrGetCurrentLogFile()
    fileLogDataDir.createOrGetNextLogFile()
    val log2 = fileLogDataDir.createOrGetNextLogFile()
    val log3 = fileLogDataDir.createOrGetNextLogFile()

    // When
    val result = fileLogDataDir.existingLogFilesAfter(Some(snapshot1))

    // Then
    result.toList must be (List(log2, log3))
  }

  test("oldestSnapshot when no snapshots are present") {
    fileLogDataDir.oldestSnapshot must be (None)
  }

  test("oldestSnapshot when snapshots are present") {
    // Given
    val snapshot0 = fileLogDataDir.createOrGetNextSnapshot()

    fileLogDataDir.createOrGetNextSnapshot() // 1
    fileLogDataDir.createOrGetNextSnapshot() // 2

    snapshot0.delete()

    // When
    val oldest = fileLogDataDir.oldestSnapshot

    // Then
    oldest.map(_.getName) must be (Some("snapshot_00000001"))
  }

  test("deleteSnapshotsExceptOldest") {
    // Given
    val snapshot0 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot1 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot2 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot3 = fileLogDataDir.createOrGetNextSnapshot()

    snapshot0.delete()

    // When
    fileLogDataDir.deleteSnapshotsExceptOldest()

    // Then
    snapshot1.exists() must be (true)
    snapshot2.exists() must be (false)
    snapshot3.exists() must be (false)
  }

  test("deleteOldDataFiles") {
    // Given
    val log0 = fileLogDataDir.createOrGetCurrentLogFile()
    val log1 = fileLogDataDir.createOrGetNextLogFile()
    val log2 = fileLogDataDir.createOrGetNextLogFile()
    val log3 = fileLogDataDir.createOrGetNextLogFile()

    val snapshot0 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot1 = fileLogDataDir.createOrGetNextSnapshot()
    val snapshot2 = fileLogDataDir.createOrGetNextSnapshot()

    // When
    fileLogDataDir.deleteOldDataFiles()

    // Then
    log0.exists() must be (false)
    log1.exists() must be (false)
    log2.exists() must be (false)
    log3.exists() must be (true)

    snapshot0.exists() must be (false)
    snapshot1.exists() must be (false)
    snapshot2.exists() must be (true)
  }
}
