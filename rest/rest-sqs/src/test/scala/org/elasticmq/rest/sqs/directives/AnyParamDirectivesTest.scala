package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.elasticmq.NodeAddress
import org.elasticmq.rest.sqs.{AWSProtocol, ContextPathModule, QueueURLModule}
import org.elasticmq.rest.sqs.model.JsonData
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.elasticmq.rest.sqs.model.RequestPayload.{JsonParams, QueryParams}
import spray.json.{JsObject, JsString}
class AnyParamDirectivesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with AnyParamDirectives
    with QueueURLModule
    with ContextPathModule {

  "anyParamsMap" should "extract both query and form parameters for query protocol" in {
    val route = path("test") {
      anyParamsMap(AWSProtocol.AWSQueryProtocol) { map =>
        _.complete(map.asInstanceOf[QueryParams].params.toList.sorted.toString())
      }
    }

    Get("/test?x=1&y=2") ~> route ~> check {
      entityAs[String] should be("List((x,1), (y,2))")
    }

    Post("/test", FormData(Map("x" -> "1", "y" -> "2"))) ~> route ~> check {
      entityAs[String] should be("List((x,1), (y,2))")
    }

    Post("/test?a=10&y=20", FormData(Map("b" -> "1", "x" -> "2"))) ~> route ~> check {
      entityAs[String] should be("List((a,10), (b,1), (x,2), (y,20))")
    }
  }

  "anyParamsMap" should "extract params from json and header for aws json protocol 1.0" in {
    val route = path("test") {
      anyParamsMap(AWSProtocol.`AWSJsonProtocol1.0`) { map =>
        _.complete(map.asInstanceOf[JsonParams].params.prettyPrint)
      }
    }

    Post("/test", JsonData(JsObject("x" -> JsString("1"), "Tags" -> JsObject("x" -> JsString("123"))))) ~> RawHeader(
      "X-Amz-Target",
      "AmazonSQS.Test"
    ) ~> route ~> check {
      entityAs[String] should be("""{
                                    |  "Tags": {
                                    |    "x": "123"
                                    |  },
                                    |  "x": "1"
                                    |}""".stripMargin)
    }

  }

  override def contextPath: String = ""
  override def serverAddress: NodeAddress = NodeAddress()
  override def awsAccountId: String = "123123333"
}
