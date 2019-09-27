package org.elasticmq.rest.sqs.model

import spray.json.{
  DefaultJsonProtocol,
  JsNumber,
  JsObject,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat,
  deserializationError
}

object RedrivePolicyJson extends DefaultJsonProtocol {

  /**
    * Regexp extracting region, account and queueName from AWS resource id
    * for example it will match arn:aws:sqs:us-west-2:123456:queue
    * and extract us-west-2, 123456 and queue in groups
    */
  private val Arn = "(?:.+:(.+)?:(.+)?:)?(.+)".r

  implicit val format: JsonFormat[RedrivePolicy] =
    new RootJsonFormat[RedrivePolicy] {
      def read(json: JsValue): RedrivePolicy = {
        json.asJsObject
          .getFields("deadLetterTargetArn", "maxReceiveCount") match {
          case Seq(JsString(Arn(region, accountId, queueName)), JsString(maxReceiveCount)) =>
            RedrivePolicy(queueName, Option(region), Option(accountId), maxReceiveCount.toInt)
          case Seq(JsString(Arn(region, accountId, queueName)), JsNumber(maxReceiveCount)) =>
            RedrivePolicy(queueName, Option(region), Option(accountId), maxReceiveCount.toInt)
          case _ =>
            deserializationError(
              "Expected fields: 'deadLetterTargetArn' (JSON string) and 'maxReceiveCount' (JSON number)"
            )
        }
      }
      def write(obj: RedrivePolicy) =
        JsObject(
          "deadLetterTargetArn" -> JsString(s"arn:aws:sqs:${obj.region.get}:${obj.accountId.get}:${obj.queueName}"),
          "maxReceiveCount" -> JsNumber(obj.maxReceiveCount)
        )
    }
}
