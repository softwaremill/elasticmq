package org.elasticmq

sealed case class MessageId(id: String) {
  override def toString = id
}