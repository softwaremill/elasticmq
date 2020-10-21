package org.elasticmq

import java.util.Base64

sealed trait MessageSystemAttribute

case class StringMessageSystemAttribute(value: String) extends MessageSystemAttribute
case class NumberMessageSystemAttribute(value: String) extends MessageSystemAttribute
case class BinaryMessageSystemAttribute(value: Array[Byte]) extends MessageSystemAttribute

object BinaryMessageSystemAttribute {
  def fromString(value: String): BinaryMessageSystemAttribute =
    BinaryMessageSystemAttribute(Base64.getDecoder.decode(value))
}