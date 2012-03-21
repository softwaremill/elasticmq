package org.elasticmq.replication.message

import org.jgroups.util.{ExposedByteArrayInputStream, ExposedByteArrayOutputStream, Util}
import java.io.ObjectOutputStream
import org.elasticmq.replication.serialization.ClassLoaderObjectInputStream

trait ReplicationMessageMarshaller {
  def serialize(message: ReplicationMessage): Array[Byte]

  def deserialize(bytes: Array[Byte]): ReplicationMessage
}

class JavaSerializationReplicationMessageMarshaller extends ReplicationMessageMarshaller {
  def serialize(message: ReplicationMessage) = {
    // Based on Util#objectToByteBuffer, but without writing the type
    val outStream = new ExposedByteArrayOutputStream(128)
    val out = new ObjectOutputStream(outStream)
    out.writeObject(message)
    out.close()
    outStream.getRawBuffer
  }

  def deserialize(bytes: Array[Byte]) = {
    // Based on Util#objectFromByteBuffer, but with a custom different ObjectInputStream
    val inStream = new ExposedByteArrayInputStream(bytes, 0, bytes.length)
    val in = new ClassLoaderObjectInputStream(inStream)
    try {
      in.readObject().asInstanceOf[ReplicationMessage]
    } finally {
      Util.close(in)
    }
  }
}
