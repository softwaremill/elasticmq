package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UnmatchedActionRoutesTest
    extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Directives
    with RespondDirectives
    with UnmatchedActionRoutes
    with ExceptionDirectives
    with AWSProtocolDirectives {

  "handleUnmatchedAction" should "return invalid action error if an action is unknown" in {
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          unmatchedAction(Map("Action" -> "Whatever"))
        }
      }
    }

    Get("/") ~> route ~> check {
      status shouldBe BadRequest
      responseAs[String] should include("<Code>InvalidAction</Code>")
    }
  }

  it should "return invalid action error if an action is empty" in {
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          unmatchedAction(Map("Action" -> ""))
        }
      }
    }

    Get("/") ~> route ~> check {
      status shouldBe BadRequest
      responseAs[String] should include("<Code>InvalidAction</Code>")
    }
  }

  it should "return missing action error if there's no action" in {
    val route = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          unmatchedAction(Map.empty)
        }
      }
    }

    Get("/") ~> route ~> check {
      status shouldBe BadRequest
      responseAs[String] should include("<Code>MissingAction</Code>")
    }
  }
}
