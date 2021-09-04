package org.elasticmq.rest.sqs.model

import org.elasticmq.rest.sqs.model.RedrivePolicy.{BackwardCompatibleRedrivePolicy, RedrivePolicy}
import spray.json.{
  DefaultJsonProtocol,
  JsNumber,
  JsObject,
  JsString,
  JsValue,
  JsonFormat,
  JsonReader,
  JsonWriter,
  deserializationError
}

object RedrivePolicyJson extends DefaultJsonProtocol {

  /** Regexp extracting region, account and queueName from AWS resource id for example it will match
    * arn:aws:sqs:us-west-2:123456:queue and extract us-west-2, 123456 and queue in groups
    */
  private val Arn = "(?:.+:(.+)?:(.+)?:)?(.+)".r

  implicit val backwardCompatibleFormat: JsonFormat[BackwardCompatibleRedrivePolicy] = lift(
    new JsonReader[BackwardCompatibleRedrivePolicy] {
      override def read(json: JsValue): BackwardCompatibleRedrivePolicy = {
        json.asJsObject
          .getFields("deadLetterTargetArn", "maxReceiveCount") match {
          case Seq(JsString(Arn(region, accountId, queueName)), JsString(maxReceiveCount)) =>
            GenericRedrivePolicy(queueName, Option(region), Option(accountId), maxReceiveCount.toInt)
          case Seq(JsString(Arn(region, accountId, queueName)), JsNumber(maxReceiveCount)) =>
            GenericRedrivePolicy(queueName, Option(region), Option(accountId), maxReceiveCount.toInt)
          case _ =>
            deserializationError(
              "Expected fields: 'deadLetterTargetArn' (JSON string) and 'maxReceiveCount' (JSON number)"
            )
        }
      }
    }
  )

  implicit val format: JsonFormat[RedrivePolicy] = lift(new JsonWriter[RedrivePolicy] {
    override def write(obj: RedrivePolicy): JsValue = {
      JsObject(
        "deadLetterTargetArn" -> JsString(s"arn:aws:sqs:${obj.region}:${obj.accountId}:${obj.queueName}"),
        "maxReceiveCount" -> JsNumber(obj.maxReceiveCount)
      )
    }
  })
}
