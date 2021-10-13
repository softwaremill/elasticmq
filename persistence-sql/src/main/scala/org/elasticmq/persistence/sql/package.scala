package org.elasticmq.persistence
import spray.json.{DefaultJsonProtocol, JsonFormat}

package object sql {

  case class SerializableAttribute(
      key: String,
      primaryDataType: String,
      stringValue: String,
      customType: Option[String]
  )

  object SerializableAttributeProtocol extends DefaultJsonProtocol {
    implicit val colorFormat: JsonFormat[SerializableAttribute] = jsonFormat4(SerializableAttribute)
  }

  object DeadLettersQueueProtocol extends DefaultJsonProtocol {
    implicit val DeadLettersQueueFormat: JsonFormat[DeadLettersQueue] = jsonFormat2(DeadLettersQueue)
  }

  import DeadLettersQueueProtocol._

  object CreateQueueProtocol extends DefaultJsonProtocol {
    implicit val CreateQueueFormat: JsonFormat[CreateQueueMetadata] = jsonFormat10(CreateQueueMetadata.apply)
  }
}
