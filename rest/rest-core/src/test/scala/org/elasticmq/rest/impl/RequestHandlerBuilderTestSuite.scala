package org.elasticmq.rest.impl

import org.scalatest.matchers.MustMatchers
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpRequest}
import org.scalatest.{mock, FunSuite}
import mock.MockitoSugar
import org.mockito.Mockito.{when}
import java.net.URI

class RequestHandlerBuilderTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  import RequestHandlerBuilder.createHandler
  import HttpMethod._

  val handler = (createHandler
          forMethod GET
          forPath "/messages/a/send"
          requiringQueryParameters List("a", "b", "c")
          running null)

  test("should not handle other methods") {
    // When
    val ret = handler.canHandle(createMockRequest(POST), new URI("/messages/a/send"))

    // Then
    ret must be (None)
  }

  test("should not handle other paths") {
    // When
    val ret = handler.canHandle(createMockRequest(GET), new URI("/somethingelse/a/send"))

    // Then
    ret must be (None)
  }

  test("should handle specified methods and paths") {
    // When
    val ret = handler.canHandle(createMockRequest(GET), new URI("/messages/a/send"))

    // Then
    ret must be (Some(Map()))
  }

  def createMockRequest(method: HttpMethod) = {
    val request = mock[HttpRequest]
    when(request.getMethod).thenReturn(method)
    request
  }
}