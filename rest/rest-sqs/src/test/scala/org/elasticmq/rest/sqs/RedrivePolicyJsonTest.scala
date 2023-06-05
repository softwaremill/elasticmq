package org.elasticmq.rest.sqs

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.elasticmq.rest.sqs.model.{GenericRedrivePolicy, RedrivePolicy}
import org.elasticmq.rest.sqs.model.RedrivePolicy.BackwardCompatibleRedrivePolicy
import spray.json._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedrivePolicyJsonTest extends AnyFlatSpec with Matchers with ScalatestRouteTest {
  "redrive policy json format" should "extract from and serialize to json" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val json = """{"deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1","maxReceiveCount":4}"""
    val expectedRedrivePolicy = GenericRedrivePolicy(
      queueName = "dlq1",
      region = Some("elasticmq"),
      accountId = Some("000000000000"),
      maxReceiveCount = 4
    )

    // Verify the policy can be derrived from a JSON string
    val rd = json.parseJson.convertTo[BackwardCompatibleRedrivePolicy]
    rd should be(expectedRedrivePolicy)
  }

  "redrive policy json format" should "serialize to json" in {
    import org.elasticmq.rest.sqs.model.RedrivePolicyJson._

    val redrivePolicy = RedrivePolicy(
      queueName = "dlq1",
      region = "elasticmq",
      accountId = "000000000000",
      maxReceiveCount = 4
    )
    val expectedJson = """{"deadLetterTargetArn":"arn:aws:sqs:elasticmq:000000000000:dlq1","maxReceiveCount":4}"""

    // Verify serializing the policy to JSON results in the expected JSON string
    redrivePolicy.toJson.compactPrint should be(expectedJson)
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

    val rd = json.parseJson.convertTo[BackwardCompatibleRedrivePolicy]

    rd should be(
      GenericRedrivePolicy(
        queueName = "dlq1",
        region = Some("elasticmq"),
        accountId = Some("000000000000"),
        maxReceiveCount = 4
      )
    )

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

    val rd = json.parseJson.convertTo[BackwardCompatibleRedrivePolicy]

    rd should be(
      GenericRedrivePolicy(
        queueName = "dlq1",
        region = None,
        accountId = None,
        maxReceiveCount = 4
      )
    )

  }
}
