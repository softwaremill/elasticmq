package org.elasticmq.rest.sqs

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

class RedrivePolicyJsonTest extends FlatSpec with Matchers with ScalatestRouteTest {
  "redrive policy json format" should "extract object from json" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val json =
      """
        |{
        |  "deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1",
        |  "maxReceiveCount":"4"
        |}
      """.stripMargin

    val rd = json.parseJson.convertTo[RedrivePolicy]

    rd should be(RedrivePolicy(
      queueName = "dlq1",
      maxReceiveCount = 4
    ))
  }

  "redrive policy json format" should "support int receive count" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val json =
      """
        |{
        |  "deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1",
        |  "maxReceiveCount":4
        |}
      """.stripMargin

    val rd = json.parseJson.convertTo[RedrivePolicy]

    rd should be(RedrivePolicy(
      queueName = "dlq1",
      maxReceiveCount = 4
    ))

  }

  "redrive policy json format" should "extract object from wrong but formerly used json" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val json =
      """
        |{
        |  "deadLetterTargetArn":"dlq1",
        |  "maxReceiveCount":"4"
        |}
      """.stripMargin

    val rd = json.parseJson.convertTo[RedrivePolicy]

    rd should be(RedrivePolicy(
      queueName = "dlq1",
      maxReceiveCount = 4
    ))

  }
}
