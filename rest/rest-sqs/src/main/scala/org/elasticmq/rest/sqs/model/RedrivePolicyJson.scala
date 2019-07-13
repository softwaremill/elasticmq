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

  implicit val format: JsonFormat[RedrivePolicy] =
    new RootJsonFormat[RedrivePolicy] {
      def read(json: JsValue): RedrivePolicy = {
        json.asJsObject
          .getFields("deadLetterTargetArn", "maxReceiveCount") match {
          case Seq(JsString(deadLetterTargetArn), JsString(maxReceiveCount)) =>
            RedrivePolicy(deadLetterTargetArn.split(":").last, maxReceiveCount.toInt)
          case Seq(JsString(deadLetterTargetArn), JsNumber(maxReceiveCount)) =>
            RedrivePolicy(deadLetterTargetArn.split(":").last, maxReceiveCount.toInt)
          case _ =>
            deserializationError(
              "Expected fields: 'deadLetterTargetArn' (JSON string) and 'maxReceiveCount' (JSON number)"
            )
        }
      }
      def write(obj: RedrivePolicy) =
        JsObject(
          "deadLetterTargetArn" -> JsString("arn:aws:sqs:elasticmq:000000000000:" + obj.queueName),
          "maxReceiveCount" -> JsNumber(obj.maxReceiveCount)
        )
    }
}
