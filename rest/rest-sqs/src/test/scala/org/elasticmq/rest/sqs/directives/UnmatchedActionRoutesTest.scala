package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.model.StatusCodes.BadRequest
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.util.ByteString
import org.elasticmq.rest.sqs.model.RequestPayload
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
          unmatchedAction(RequestPayload.QueryParams(Map("Action" -> "Whatever")))
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
          unmatchedAction(RequestPayload.QueryParams(Map("Action" -> "")))
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
          unmatchedAction(RequestPayload.QueryParams(Map.empty))
        }
      }
    }

    Get("/") ~> route ~> check {
      status shouldBe BadRequest
      responseAs[String] should include("<Code>MissingAction</Code>")
    }

    Post("/").withEntity(AmzJsonProtocol.contentType("1.0"), ByteString.empty) ~> route ~> check {
      status shouldBe BadRequest
      responseAs[String] should include(""""Message":"MissingAction; see the SQS docs."""")
    }
  }
}
