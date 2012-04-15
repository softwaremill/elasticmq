package org.elasticmq.replication.message

import org.elasticmq.marshalling.JavaSerializationMarshaller

trait ReplicationMessageMarshaller {
  def serialize(message: ReplicationMessage): Array[Byte]

  def deserialize(bytes: Array[Byte]): ReplicationMessage
}

class JavaSerializationReplicationMessageMarshaller extends ReplicationMessageMarshaller {
  val marshaller = new JavaSerializationMarshaller

  def serialize(message: ReplicationMessage) = marshaller.serialize(message)

  def deserialize(bytes: Array[Byte]) = marshaller.deserialize(bytes).asInstanceOf[ReplicationMessage]
}
