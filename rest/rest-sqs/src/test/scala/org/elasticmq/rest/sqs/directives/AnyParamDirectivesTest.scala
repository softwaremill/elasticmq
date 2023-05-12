package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.elasticmq.rest.sqs.AWSProtocol
import org.elasticmq.rest.sqs.model.JsonData
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnyParamDirectivesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with AnyParamDirectives {

  "anyParamsMap" should "extract both query and form parameters for query protocol" in {
    val route = path("test") {
      anyParamsMap(AWSProtocol.AWSQueryProtocol) { map => _.complete(map.toList.sorted.toString()) }
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
      anyParamsMap(AWSProtocol.`AWSJsonProtocol1.0`) { map => _.complete(map.toList.sorted.toString()) }
    }

    Post("/test", JsonData(Map("x" -> "1", "y" -> "2"))) ~> RawHeader(
      "X-Amz-Target",
      "AmazonSQS.Test"
    ) ~> route ~> check {
      entityAs[String] should be("List((Action,Test), (x,1), (y,2))")
    }

  }
}
