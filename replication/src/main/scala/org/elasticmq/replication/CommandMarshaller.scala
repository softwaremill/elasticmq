package org.elasticmq.replication

import org.elasticmq.storage.IdempotentMutativeCommand
import org.jgroups.util.Util

trait CommandMarshaller {
  def serialize(commands: Seq[IdempotentMutativeCommand[_]]): Array[Byte]
  def deserialize(bytes: Array[Byte]): Seq[IdempotentMutativeCommand[_]]
}

class JavaSerializationCommandMarshaller extends CommandMarshaller {
  def serialize(commands: Seq[IdempotentMutativeCommand[_]]) = {
    Util.objectToByteBuffer(commands)
  }

  def deserialize(bytes: Array[Byte]) = {
    Util.objectFromByteBuffer(bytes).asInstanceOf[Seq[IdempotentMutativeCommand[_]]]
  }
}
