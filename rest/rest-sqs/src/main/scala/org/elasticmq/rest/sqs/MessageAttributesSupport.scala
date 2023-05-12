package org.elasticmq.rest.sqs

import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import spray.json.{JsObject, JsString, JsValue, JsonReader, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

trait MessageAttributesSupport {

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
        case "Number" => NumberMessageAttribute(pickField[String]("StringValue"))
        case "String" => StringMessageAttribute(pickField[String]("StringValue"))
        case "Binary" => BinaryMessageAttribute(pickField[Array[Byte]]("BinaryValue"))
      }
    }
  }

}
