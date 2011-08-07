package org.elasticmq.rest

import org.scalatest.matchers.MustMatchers
import org.scalatest.{mock, FunSuite}
import mock.MockitoSugar
import org.mockito.Mockito.{when}
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpRequest}
import org.jboss.netty.buffer.ChannelBuffer
import java.nio.charset.Charset
import org.mockito.Matchers

class RequestHandlerBuilderTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  import RequestHandlerBuilder.createHandler
  import RestPath._
  import HttpMethod._

  val handler1 = (createHandler
          forMethod GET
          forPath (root / "messages" / "a" / "send")
          requiringParameters List()
          running null)

  test("should not handle other methods") {
    // When
    val ret = handler1.canHandle(createMockRequest(POST, "/messages/a/send"), Map())

    // Then
    ret must be (None)
  }

  test("should not handle other paths") {
    // When
    val ret = handler1.canHandle(createMockRequest(GET, "/somethingelse/a/send"), Map())

    // Then
    ret must be (None)
  }

  test("should handle specified methods and paths") {
    // When
    val ret = handler1.canHandle(createMockRequest(GET, "/messages/a/send"), Map())

    // Then
    ret must be (Some(Map()))
  }

  val handler2 = (createHandler
          forMethod POST
          forPath (root / "x" / "y")
          requiringParameters List("a", "b")
          running null)

  test("should check for required parameters") {
    // When
    val ret = handler2.canHandle(createMockRequest(POST, "/x/y?a=10&c=20"), Map())

    // Then
    ret must be (None)
  }

  test("should return found parameters") {
    // When
    val ret = handler2.canHandle(createMockRequest(POST, "/x/y?a=10&b=20"), Map())

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "20")))
  }

  test("should include optional parameters") {
    // When
    val ret = handler2.canHandle(createMockRequest(POST, "/x/y?a=10&b=20&c=30"), Map())

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "20", "c" -> "30")))
  }

  val handler3 = (createHandler
          forMethod POST
          forPath (root / "x" / %("b") / "y")
          requiringParameters List("a")
          running null)

  test("should include path parameters") {
    // When
    val ret = handler3.canHandle(createMockRequest(POST, "/x/34/y?a=10"), Map())

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "34")))
  }

  val handler4 = (createHandler
          forMethod POST
          forPath (root / "x" / %("b"))
          requiringParameterValues Map("a" -> "10", "b" -> "20")
          running null)

  test("should check for query parameter values") {
    // When
    val ret = handler4.canHandle(createMockRequest(POST, "/x/34?a=10"), Map())

    // Then
    ret must be (None)
  }

  test("should check for path parameter values") {
    // When
    val ret = handler4.canHandle(createMockRequest(POST, "/x/20?a=11"), Map())

    // Then
    ret must be (None)
  }

  test("should handle correct parameters values") {
    // When
    val ret = handler4.canHandle(createMockRequest(POST, "/x/20?a=10"), Map())

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "20")))
  }

  val handler5 = (createHandler
          forMethod POST
          forPath (root / "x")
          includingParametersFromBody ()
          running null)

  test("should include parameters from body") {
    // When
    val ret = handler5.canHandle(createMockRequest(POST, "/x", "a=10&b=20"), Map())

    // Then
    ret must be (Some(Map("a" -> "10", "b" -> "20")))
  }

  val handler6 = (createHandler
          forMethod POST
          forPath (root / "x")
          includingParametersFromBody ()
          requiringParameters List("a", "b", "c")
          running null)

  test("should check for body parameters") {
    // When
    val ret = handler6.canHandle(createMockRequest(POST, "/x", "a=10&b=20"), Map())

    // Then
    ret must be (None)
  }

  def createMockRequest(method: HttpMethod, path: String, body: String = "") = {
    val content = mock[ChannelBuffer]
    when(content.toString(Matchers.any(classOf[Charset]))).thenReturn(body)

    val request = mock[HttpRequest]
    when(request.getMethod).thenReturn(method)
    when(request.getUri).thenReturn(path)
    when(request.getContent).thenReturn(content)
    request
  }
}