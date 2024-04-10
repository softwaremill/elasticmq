package org.elasticmq.persistence.sql
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import spray.json.{DefaultJsonProtocol, JsonFormat}

case class SerializableAttribute(
    key: String,
    primaryDataType: String,
    stringValue: String,
    customType: Option[String]
)

object SerializableAttributeProtocol extends DefaultJsonProtocol {
  implicit val colorFormat: JsonFormat[SerializableAttribute] = jsonFormat4(SerializableAttribute.apply)
}

object DeadLettersQueueProtocol extends DefaultJsonProtocol {
  implicit val DeadLettersQueueFormat: JsonFormat[DeadLettersQueue] = jsonFormat2(DeadLettersQueue.apply)
}

import org.elasticmq.persistence.sql.DeadLettersQueueProtocol._

object CreateQueueProtocol extends DefaultJsonProtocol {
  implicit val CreateQueueFormat: JsonFormat[CreateQueueMetadata] = jsonFormat12(CreateQueueMetadata.apply)
}
