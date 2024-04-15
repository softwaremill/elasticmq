package org.elasticmq

import java.nio.ByteBuffer
import java.util.Base64

sealed abstract class MessageAttribute(val customType: Option[String]) {
  protected val primaryDataType: String

  def getDataType(): String =
    customType match {
      case Some(t) => s"$primaryDataType.$t"
      case None    => primaryDataType
    }
}

case class StringMessageAttribute(stringValue: String, override val customType: Option[String] = None)
    extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "String"
}

case class NumberMessageAttribute(stringValue: String, override val customType: Option[String] = None)
    extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "Number"
}

case class BinaryMessageAttribute(binaryValue: Seq[Byte], override val customType: Option[String] = None)
    extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "Binary"

  def asBase64: String = Base64.getEncoder.encodeToString(binaryValue.toArray)
}

object BinaryMessageAttribute {
  def fromBase64(base64Str: String, customType: Option[String] = None): BinaryMessageAttribute =
    BinaryMessageAttribute(
      binaryValue = Base64.getDecoder.decode(base64Str).toSeq,
      customType = customType
    )

  def fromByteBuffer(byteBuffer: ByteBuffer, customType: Option[String] = None): BinaryMessageAttribute =
    BinaryMessageAttribute(
      binaryValue = {
        byteBuffer.clear()
        val value = new Array[Byte](byteBuffer.capacity())
        byteBuffer.get(value)
        value
      }.toSeq,
      customType = customType
    )
}
