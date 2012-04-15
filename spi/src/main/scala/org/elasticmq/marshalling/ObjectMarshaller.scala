package org.elasticmq.marshalling

trait ObjectMarshaller {
  def serialize(obj: AnyRef): Array[Byte]
  def deserialize(bytes: Array[Byte]): AnyRef
}
