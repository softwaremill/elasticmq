package org.elasticmq.storage.filelog

import org.scalatest.matchers.MustMatchers
import org.elasticmq.data.{QueueData, MessageData}
import org.elasticmq.{MillisVisibilityTimeout, MillisNextDelivery, MessageId}
import org.joda.time.{Duration, DateTime}
import org.elasticmq.storage.{DeleteMessageCommand, CreateQueueCommand, SendMessageCommand}
import org.scalatest.{BeforeAndAfter, FunSuite}
import java.io.{DataOutputStream, FileOutputStream, File}

class CommandWriterReaderTest extends FunSuite with MustMatchers with BeforeAndAfter {
  val cmds = SendMessageCommand("q1", MessageData(MessageId("id1"), None, "xyz", MillisNextDelivery(12562L), new DateTime())) ::
    CreateQueueCommand(QueueData("q2", MillisVisibilityTimeout(10000L), Duration.ZERO, new DateTime(), new DateTime())) ::
    DeleteMessageCommand("q3", MessageId("123912-12412-124182412")) :: Nil

  var file: File = _

  before {
    file = File.createTempFile("command", "tests")
  }

  after {
    file.delete()
  }

  test("should write and read commands") {
    // When
    val writer = CommandWriter.create(file)
    cmds.foreach(writer.write(_))
    writer.close()

    val readCmds = CommandReader.create(file).readStream().toList

    // Then
    readCmds must be (cmds)
  }

  for ((numberOfBytes, errorName) <- (50, "truncated") :: (100, "wrong CRC") :: Nil) {
    test("should read commands despite a corrupt file: " + errorName) {
      val writer = CommandWriter.create(file)
      writer.write(cmds(0))
      writer.write(cmds(1))
      writer.close()

      val outputStream = new DataOutputStream(new FileOutputStream(file, true))
      outputStream.writeInt(100)
      outputStream.writeLong(500L)
      outputStream.write(new Array[Byte](numberOfBytes))
      outputStream.close()

      val readCmds = CommandReader.create(file).readStream().toList

      // Then
      readCmds must be (cmds.take(2))
    }
  }
}
