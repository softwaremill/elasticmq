package org.elasticmq.marshalling

import java.io.{ByteArrayInputStream, ObjectOutputStream}

class JavaSerializationMarshaller extends ObjectMarshaller {
  def serialize(message: AnyRef) = {
    // Based on Util#objectToByteBuffer, but without writing the type
    val outStream = new ExposedByteArrayOutputStream(512)
    val out = new ObjectOutputStream(outStream)
    out.writeObject(message)
    out.close()
    outStream.getRawBuffer
  }

  def deserialize(bytes: Array[Byte]) = {
    // Based on Util#objectFromByteBuffer, but with a custom different ObjectInputStream
    val inStream = new ByteArrayInputStream(bytes, 0, bytes.length)
    val in = new ClassLoaderObjectInputStream(inStream)
    try {
      in.readObject()
    } finally {
      try { in.close() } catch { case _: Exception => }
    }
  }
}
