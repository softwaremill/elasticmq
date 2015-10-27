package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{ShouldMatchers, FlatSpec}

class AnyParamDirectivesTest extends FlatSpec with ShouldMatchers with ScalatestRouteTest
  with Directives with AnyParamDirectives {

  "anyParamsMap" should "extract both query and form parameters" in {
    val route = path("test") {
      anyParamsMap { map => _.complete(map.toList.sorted.toString()) }
    }

    Get("/test?x=1&y=2") ~> route ~> check {
      entityAs[String] should be ("List((x,1), (y,2))")
    }

    Post("/test", FormData(Map("x" -> "1", "y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("List((x,1), (y,2))")
    }

    Post("/test?a=10&y=20", FormData(Map("b" -> "1", "x" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("List((a,10), (b,1), (x,2), (y,20))")
    }
  }
}