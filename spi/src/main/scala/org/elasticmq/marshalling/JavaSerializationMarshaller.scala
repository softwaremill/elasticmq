package org.elasticmq.marshalling

import java.io.{ByteArrayInputStream, ObjectOutputStream}

class JavaSerializationMarshaller {
  def serialize(message: AnyRef): Array[Byte] = {
    // Based on Util#objectToByteBuffer, but without writing the type
    val outStream = new ExposedByteArrayOutputStream(128)
    val out = new ObjectOutputStream(outStream)
    out.writeObject(message)
    out.close()
    outStream.getRawBuffer
  }

  def deserialize(bytes: Array[Byte]): AnyRef = {
    // Based on Util#objectFromByteBuffer, but with a custom different ObjectInputStream
    val inStream = new ByteArrayInputStream(bytes, 0, bytes.length)
    val in = new ClassLoaderObjectInputStream(inStream)
    try {
      in.readObject()
    } finally {
      try { in.close() } catch { case _ => }
    }
  }
}
