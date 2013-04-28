package org.elasticmq.rest.sqs.directives

import spray.testkit.ScalatestRouteTest
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import spray.http.FormData
import spray.routing._

class AnyParamDirectivesTest extends FlatSpec with ShouldMatchers with ScalatestRouteTest
  with Directives with AnyParamDirectives {

  it should "work with a single required parameter" in {
    val route = path("test") {
      anyParam("x") { x => _.complete(s"$x") }
    }

    Get("/test?x=1") ~> route ~> check {
      entityAs[String] should be ("1")
    }

    Post("/test", FormData(Map("x" -> "1"))) ~> route ~> check {
      entityAs[String] should be ("1")
    }
  }

  it should "work with a single optional parameter" in {
    val route = path("test") {
      anyParam("x"?) { x => _.complete(s"$x") }
    }

    Get("/test?x=1") ~> route ~> check {
      entityAs[String] should be ("Some(1)")
    }

    Post("/test", FormData(Map("x" -> "1"))) ~> route ~> check {
      entityAs[String] should be ("Some(1)")
    }

    Get("/test") ~> route ~> check {
      entityAs[String] should be ("None")
    }

    Post("/test", FormData(Map())) ~> route ~> check {
      entityAs[String] should be ("None")
    }
  }

  it should "work with double required parameters" in {
    val route = path("test") {
      anyParam("x", "y") { (x, y) => _.complete(s"$x $y") }
    }

    Get("/test?x=1&y=2") ~> route ~> check {
      entityAs[String] should be ("1 2")
    }

    Post("/test", FormData(Map("x" -> "1", "y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("1 2")
    }
  }

  it should "work with double optional parameters" in {
    val route = path("test") {
      anyParam("x"?, "y"?) { (x, y) => _.complete(s"$x $y") }
    }

    Get("/test?x=1&y=2") ~> route ~> check {
      entityAs[String] should be ("Some(1) Some(2)")
    }

    Post("/test", FormData(Map("x" -> "1", "y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("Some(1) Some(2)")
    }

    Get("/test?x=1") ~> route ~> check {
      entityAs[String] should be ("Some(1) None")
    }

    Post("/test", FormData(Map("y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("None Some(2)")
    }
  }

  it should "work with mixed parameters" in {
    val route = path("test") {
      anyParam("x"?, "y") { (x, y) => _.complete(s"$x $y") }
    }

    Get("/test?x=1&y=2") ~> route ~> check {
      entityAs[String] should be ("Some(1) 2")
    }

    Post("/test", FormData(Map("x" -> "1", "y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("Some(1) 2")
    }

    Get("/test?y=2") ~> route ~> check {
      entityAs[String] should be ("None 2")
    }

    Post("/test", FormData(Map("y" -> "2"))) ~> route ~> check {
      entityAs[String] should be ("None 2")
    }
  }

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