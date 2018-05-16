package org.elasticmq.rest.sqs

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

class RedrivePolicyJsonTest extends FlatSpec with Matchers with ScalatestRouteTest {
  "redrive policy json format" should "extract from and serialize to json" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val json = """{"deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1","maxReceiveCount":4}"""
    val expectedRedrivePolicy = RedrivePolicy(queueName = "dlq1", maxReceiveCount = 4)

    // Verify the policy can be derrived from a JSON string
    val rd = json.parseJson.convertTo[RedrivePolicy]
    rd should be(expectedRedrivePolicy)

    // Verify serializing the policy to JSON results in the expected JSON string
    rd.toJson.compactPrint should be(json)
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

    rd should be(
      RedrivePolicy(
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

    rd should be(
      RedrivePolicy(
        queueName = "dlq1",
        maxReceiveCount = 4
      ))

  }
}
