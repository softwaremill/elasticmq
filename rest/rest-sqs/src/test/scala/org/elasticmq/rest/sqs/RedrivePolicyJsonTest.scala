package org.elasticmq.rest.sqs

import java.net.URI

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import spray.json._

class RedrivePolicyJsonTest extends FlatSpec with Matchers with ScalatestRouteTest {
  "redrive policy json format" should "extract object from json" in {
    import RedrivePolicyJson._

    val json =
      """
        |{
        |  "queueName":"dlq1",
        |  "maxReceiveCount":4,
        |  "defaultVisibilityTimeout":4,
        |  "delay":4,
        |  "receiveMessageWait":4
        |}
      """.stripMargin

    val rd = json.parseJson.convertTo[RedrivePolicy]

    rd should be(RedrivePolicy(
      queueName = "dlq1",
      maxReceiveCount = 4,
      defaultVisibilityTimeout = Some(4),
      delay = Some(4),
      receiveMessageWait = Some(4)
    ))

  }
}
