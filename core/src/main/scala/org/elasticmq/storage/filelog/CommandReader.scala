package org.elasticmq.storage.filelog

import org.elasticmq.storage.IdempotentMutativeCommand
import com.weiglewilczek.slf4s.Logging
import org.elasticmq.marshalling.{JavaSerializationMarshaller, ObjectMarshaller}
import java.io._
import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
class CommandReader(inputStream: DataInputStream, objectMarshaller: ObjectMarshaller) extends Logging with Closeable {
  val crc = new CRC

  def read(): Option[IdempotentMutativeCommand[_]] = {
    try {
      val lengthOption = readOptionalInt()
      lengthOption.map(length => {
        val buffer = readBuffer(length)
        objectMarshaller.deserialize(buffer).asInstanceOf[IdempotentMutativeCommand[_]]
      })
    } catch {
      case e@(_ :IOException | _ :EOFException) => {
        logger.error("Exception when reading commands.", e)
        None
      }
    }
  }

  private def readOptionalInt(): Option[Int] = {
    val ch1 = inputStream.read()
    if (ch1 < 0) {
      None
    } else {
      val ch2 = inputStream.read()
      val ch3 = inputStream.read()
      val ch4 = inputStream.read()
      if ((ch2 | ch3 | ch4) < 0)
        throw new EOFException()
      Some((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0))
    }
  }

  private def readBuffer(length: Int) = {
    val storedCrc = inputStream.readLong()

    val buffer = new Array[Byte](length)
    inputStream.readFully(buffer)

    val actualCrc = crc.of(buffer)

    if (storedCrc != actualCrc) {
      throw new IOException("CRC mismatch: expected %d, got %d".format(storedCrc, actualCrc))
    }

    buffer
  }

  def readStream(): Stream[IdempotentMutativeCommand[_]] = {
    read() match {
      case Some(cmd) => Stream.cons(cmd, readStream())
      case None => Stream.empty
    }
  }

  @throws(classOf[IOException])
  def close() {
    inputStream.close()
  }
}

object CommandReader {
  def create(readFrom: File) = {
    new CommandReader(
      new DataInputStream(new BufferedInputStream(new FileInputStream(readFrom))),
      new JavaSerializationMarshaller)
  }
}
