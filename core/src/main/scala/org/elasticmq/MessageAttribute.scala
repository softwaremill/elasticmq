package org.elasticmq

import javax.xml.bind.DatatypeConverter

sealed abstract class MessageAttribute(val customType: Option[String]) {
  protected val primaryDataType: String

  def getDataType() = customType match {
    case Some(t) => s"$primaryDataType.t"
    case None    => primaryDataType
  }
}

case class StringMessageAttribute(stringValue: String, override val customType: Option[String]) extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "String"
}

case class BinaryMessageAttribute(binaryValue: Array[Byte], override val customType: Option[String]) extends MessageAttribute(customType) {
  protected override val primaryDataType: String = "Binary"

  def asBase64 = DatatypeConverter.printBase64Binary(binaryValue)
}
object BinaryMessageAttribute {
  def fromBase64(base64Str: String, customType: Option[String]) = BinaryMessageAttribute(
    binaryValue = DatatypeConverter.parseBase64Binary(base64Str),
    customType = customType
  )
}