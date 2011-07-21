package org.elasticmq.rest

import org.scalatest.matchers.MustMatchers
import org.scalatest.{mock, FunSuite}
import mock.MockitoSugar
import org.mockito.Mockito.{when}
import org.jboss.netty.handler.codec.http.{QueryStringDecoder, HttpMethod, HttpRequest}

class RequestHandlerBuilderTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  import RequestHandlerBuilder.createHandler
  import RestPath._
  import HttpMethod._

  val handler1 = (createHandler
          forMethod GET
          forPath (root / "messages" / "a" / "send")
          requiringQueryParameters List()
          running null)

  val handler2 = (createHandler
          forMethod POST
          forPath (root / "x" / "y")
          requiringQueryParameters List("a", "b")
          running null)

  test("should not handle other methods") {
    // When
    val ret = handler1.canHandle(createMockRequest(POST), new QueryStringDecoder("/messages/a/send"))

    // Then
    ret must be (None)
  }

  test("should not handle other paths") {
    // When
    val ret = handler1.canHandle(createMockRequest(GET), new QueryStringDecoder("/somethingelse/a/send"))

    // Then
    ret must be (None)
  }

  test("should handle specified methods and paths") {
    // When
    val ret = handler1.canHandle(createMockRequest(GET), new QueryStringDecoder("/messages/a/send"))

    // Then
    ret must be (Some(Map()))
  }

  test("should check for required parameters") {
    // When
    val ret = handler2.canHandle(createMockRequest(POST), new QueryStringDecoder("/x/y?a=10&c=20"))

    // Then
    ret must be (None)
  }

  test("should return found parameters") {
    // When
    val ret = handler2.canHandle(createMockRequest(POST), new QueryStringDecoder("/x/y?a=10&b=20"))

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "20")))
  }

  def createMockRequest(method: HttpMethod) = {
    val request = mock[HttpRequest]
    when(request.getMethod).thenReturn(method)
    request
  }
}