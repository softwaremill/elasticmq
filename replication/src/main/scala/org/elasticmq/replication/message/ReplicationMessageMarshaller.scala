package org.elasticmq.replication.message

import org.jgroups.util.Util

trait ReplicationMessageMarshaller {
  def serialize(message: ReplicationMessage): Array[Byte]

  def deserialize(bytes: Array[Byte]): ReplicationMessage
}

class JavaSerializationReplicationMessageMarshaller extends ReplicationMessageMarshaller {
  def serialize(message: ReplicationMessage) = {
    Util.objectToByteBuffer(message)
  }

  def deserialize(bytes: Array[Byte]) = {
    Util.objectFromByteBuffer(bytes).asInstanceOf[ReplicationMessage]
  }
}
