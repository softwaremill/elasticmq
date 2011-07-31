package org.elasticmq.rest

import org.scalatest.matchers.MustMatchers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpMethod}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.HttpClient
import org.apache.http.util.EntityUtils
import org.scalatest.{BeforeAndAfterAll, FunSuite}

class RestServerTestSuite extends FunSuite with MustMatchers with BeforeAndAfterAll {
  import RequestHandlerBuilder.createHandler
  import RestPath._
  import HttpMethod._

  val echoParamsHandler = (createHandler
          forMethod GET
          forPath (root / "echo" / "params")
          requiringQueryParameters List()
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      StringResponse("OK " + parameters)
    }
  })

  val httpClient: HttpClient = new DefaultHttpClient()

  test("should echo parameters") {
    val server = RestServer.start(echoParamsHandler :: Nil, 8888)

    val action = new HttpGet("http://localhost:8888/echo/params?param1=z&param2=x")
    val response = httpClient.execute(action)

    val responseString = EntityUtils.toString(response.getEntity)

    responseString must include ("param1 -> z")
    responseString must include ("param2 -> x")

    server.stop()
  }

  override protected def afterAll() {
    httpClient.getConnectionManager.shutdown()
  }
}