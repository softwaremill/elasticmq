package org.elasticmq.rest.stats

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue, RootJsonFormat, deserializationError}

trait StatisticsJsonFormat extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object queueStatisticsFormat extends RootJsonFormat[QueueStatisticsResponse] {
    override def write(obj: QueueStatisticsResponse): JsValue = JsObject(
      "approximateNumberOfVisibleMessages" -> JsNumber(obj.approximateNumberOfVisibleMessages),
      "approximateNumberOfInvisibleMessages" -> JsNumber(obj.approximateNumberOfInvisibleMessages),
      "approximateNumberOfMessagesDelayed" -> JsNumber(obj.approximateNumberOfMessagesDelayed)
    )

    override def read(json: JsValue): QueueStatisticsResponse = json.asJsObject.getFields(
      "approximateNumberOfVisibleMessages",
      "approximateNumberOfInvisibleMessages",
      "approximateNumberOfMessagesDelayed"
    ) match {
      case Seq(JsNumber(visibleMessages), JsNumber(invisibleMessages), JsNumber(delayedMessages)) =>
        QueueStatisticsResponse(visibleMessages.toLong, invisibleMessages.toLong, delayedMessages.toLong)
      case _ => deserializationError(s"Could not deserialize $json to QueueStatisticsResponse")
    }
  }
  implicit object queuesFormat extends RootJsonFormat[QueuesResponse] {
    override def write(obj: QueuesResponse): JsValue = JsObject(
      "name" -> JsString(obj.name),
      "statistics" -> queueStatisticsFormat.write(obj.statistics)
    )

    override def read(json: JsValue): QueuesResponse = json.asJsObject.getFields("name", "statistics") match {
      case Seq(JsString(name), statistics @ JsObject(_)) => QueuesResponse(name, queueStatisticsFormat.read(statistics))
      case _                                             => throw new IllegalArgumentException("Invalid json")
    }
  }
  implicit object queueFormat extends RootJsonFormat[QueueResponse] {
    override def write(obj: QueueResponse): JsValue = JsObject(
      "name" -> JsString(obj.name),
      "attributes" -> JsObject(
        obj.attributes.map { case (key, value) =>
          key -> JsString(value)
        }.toList
      )
    )

    override def read(json: JsValue): QueueResponse = json.asJsObject.getFields("name", "attributes") match {
      case Seq(JsString(name), JsObject(attributes)) =>
        QueueResponse(
          name,
          attributes.map {
            case (name, JsString(value)) => name -> value
            case _                       => deserializationError(s"Could not deserialize $json to QueueResponse")
          }
        )
      case _ => throw new IllegalArgumentException("Invalid json")
    }
  }
}
