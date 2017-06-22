package org.elasticmq.rest.sqs

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

class RedrivePolicyJsonTest extends FlatSpec with Matchers with ScalatestRouteTest {
  "redrive policy json format" should "extract object from json" in {
    import RedrivePolicyJson._

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
      maxReceiveCount = 4.toInt
    ))

  }
}
