package org.elasticmq.rest.sqs

import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsObject, JsString, JsValue, JsonReader, RootJsonFormat}

trait MessageAttributesSupport {

  private val SomeString = """String\.?(.*)""".r
  private val SomeNumber = """Number\.?(.*)""".r
  private val SomeBinary = """Binary\.?(.*)""".r

  implicit val messageAttributeJsonFormat: RootJsonFormat[MessageAttribute] = new RootJsonFormat[MessageAttribute] {

    override def write(obj: MessageAttribute): JsValue = obj match {
      case NumberMessageAttribute(value, _) =>
        JsObject("DataType" -> JsString("Number"), "StringValue" -> JsString(value))
      case StringMessageAttribute(value, _) =>
        JsObject("DataType" -> JsString("String"), "StringValue" -> JsString(value))
      case msg: BinaryMessageAttribute =>
        JsObject("DataType" -> JsString("Binary"), "BinaryValue" -> JsString(msg.asBase64))
    }

    override def read(json: JsValue): MessageAttribute = {
      val fields = json.asJsObject.fields

      def pickField[O: JsonReader](name: String) =
        fields.getOrElse(name, throw new SQSException(s"Field $name is required")).convertTo[O]

      val dataType = pickField[String]("DataType")
      dataType match {
        case SomeNumber(ct) => NumberMessageAttribute(pickField[String]("StringValue"), customType(ct))
        case SomeString(ct) => StringMessageAttribute(pickField[String]("StringValue"), customType(ct))
        case SomeBinary(ct) => BinaryMessageAttribute(pickField[Array[Byte]]("BinaryValue"), customType(ct))
        case _ =>
          throw new Exception("Currently only handles String, Number and Binary typed attributes")
      }
    }

    private def customType(appendix: String) = if (appendix.isEmpty) None else Some(appendix)
  }

}
