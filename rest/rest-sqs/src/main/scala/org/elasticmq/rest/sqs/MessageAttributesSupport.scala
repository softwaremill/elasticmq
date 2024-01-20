package org.elasticmq.rest.sqs

import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import spray.json.DefaultJsonProtocol._
import spray.json.{JsArray, JsObject, JsString, JsValue, JsonReader, RootJsonFormat}

trait MessageAttributesSupport {

  private val SomeString = """String\.?(.*)""".r
  private val SomeNumber = """Number\.?(.*)""".r
  private val SomeBinary = """Binary\.?(.*)""".r

  implicit val messageAttributeJsonFormat: RootJsonFormat[MessageAttribute] = new RootJsonFormat[MessageAttribute] {

    override def write(obj: MessageAttribute): JsValue = obj match {
      case NumberMessageAttribute(value, customType) =>
        JsObject("DataType" -> JsString("Number" + customTypeAsString(customType)), "StringValue" -> JsString(value))
      case StringMessageAttribute(value, customType) =>
        JsObject("DataType" -> JsString("String" + customTypeAsString(customType)), "StringValue" -> JsString(value))
      case msg: BinaryMessageAttribute =>
        JsObject(
          "DataType" -> JsString("Binary" + customTypeAsString(msg.customType)),
          "BinaryValue" -> JsString(msg.asBase64)
        )
    }

    override def read(json: JsValue): MessageAttribute = {
      val fields = json.asJsObject.fields

      def pickFieldRaw(name: String) =
        fields.getOrElse(name, throw new SQSException(s"Field $name is required"))

      def pickField[O: JsonReader](name: String) =
        pickFieldRaw(name).convertTo[O]

      val dataType = pickField[String]("DataType")
      dataType match {
        case SomeNumber(ct) => NumberMessageAttribute(pickField[String]("StringValue"), customType(ct))
        case SomeString(ct) => StringMessageAttribute(pickField[String]("StringValue"), customType(ct))
        case SomeBinary(ct) =>
          pickFieldRaw("BinaryValue") match {
            case arr: JsArray  => BinaryMessageAttribute(arr.convertTo[Array[Byte]], customType(ct))
            case str: JsString => BinaryMessageAttribute.fromBase64(str.convertTo[String], customType(ct))
            case any: Any      => throw new SQSException(s"Field BinaryValue has unsupported type $any")
          }
        case _ =>
          throw new Exception("Currently only handles String, Number and Binary typed attributes")
      }
    }

    private def customType(appendix: String) = if (appendix.isEmpty) None else Some(appendix)

    private def customTypeAsString(customType: Option[String]) = customType.fold("")(t => s".$t")
  }

}
