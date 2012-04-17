package org.elasticmq.storage.filelog

import org.elasticmq.storage.IdempotentMutativeCommand
import java.io._
import org.elasticmq.marshalling.{JavaSerializationMarshaller, ObjectMarshaller}
import java.util.zip.CRC32
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class CommandWriter(outputStream: DataOutputStream, objectMarshaller: ObjectMarshaller) extends Closeable{
  private val crc = new CRC

  /**
   * Each command is written to the stream as:
   * - number of bytes (int)
   * - crc (long)
   * - the bytes
   */
  @throws(classOf[IOException])
  def write(command: IdempotentMutativeCommand[_]) {
    val serialized = objectMarshaller.serialize(command)
    outputStream.writeInt(serialized.length)
    outputStream.writeLong(crc.of(serialized))
    outputStream.write(serialized)
  }

  @throws(classOf[IOException])
  def flush() {
    outputStream.flush()
  }

  @throws(classOf[IOException])
  def close() {
    outputStream.close()
  }
}

object CommandWriter {
  def create(writeTo: File) = {
    new CommandWriter(
      new DataOutputStream(new BufferedOutputStream(new FileOutputStream(writeTo))),
      new JavaSerializationMarshaller)
  }
}

class CRC {
  private val crc32 = new CRC32()

  def of(bytes: Array[Byte]): Long = {
    crc32.reset()
    crc32.update(bytes)
    crc32.getValue
  }
}