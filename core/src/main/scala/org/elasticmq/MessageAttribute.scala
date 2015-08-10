package org.elasticmq

import java.nio.ByteBuffer
import javax.xml.bind.DatatypeConverter

sealed abstract class MessageAttribute(val customType: Option[String]) {
  protected val primaryDataType: String

  def getDataType() = customType match {
    case Some(t) => s"$primaryDataType.t"
    case None    => primaryDataType
  }
}

case class StringMessageAttribute(stringValue: String, override val customType: Option[String] = None) extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "String"
}

case class BinaryMessageAttribute(binaryValue: Array[Byte], override val customType: Option[String] = None) extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "Binary"

  def asBase64 = DatatypeConverter.printBase64Binary(binaryValue)
}
object BinaryMessageAttribute {
  def fromBase64(base64Str: String, customType: Option[String] = None) = BinaryMessageAttribute(
    binaryValue = DatatypeConverter.parseBase64Binary(base64Str),
    customType = customType
  )

  def fromByteBuffer(byteBuffer: ByteBuffer, customType: Option[String] = None) = BinaryMessageAttribute(
    binaryValue = {
      byteBuffer.clear()
      val value = new Array[Byte](byteBuffer.capacity())
      byteBuffer.get(value)
      value
    },
    customType = customType
  )
}