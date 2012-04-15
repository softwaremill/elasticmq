package org.elasticmq.rest

import org.scalatest.matchers.MustMatchers
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpMethod}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.HttpClient
import org.apache.http.util.EntityUtils
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.apache.http.client.methods.{HttpPost, HttpGet}
import org.apache.http.entity.StringEntity
import java.net.InetSocketAddress

class RestServerTestSuite extends FunSuite with MustMatchers with BeforeAndAfterAll {
  import RequestHandlerBuilder.createHandler
  import RestPath._
  import HttpMethod._

  val TestPort = 8888
  val TestHost = "http://localhost:"+TestPort
  
  val echoParamsHandler = (createHandler
          forMethod GET
          forPath (root / "echo" / "params")
          requiringParameters List()
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      StringResponse("OK " + parameters)
    }
  })

  val echo2ParamsHandler = (createHandler
          forMethod POST
          forPath (root / "echo2" / "params2")
          requiringParameters List("a", "b")
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      StringResponse("KO " + parameters)
    }
  })

  val exceptionThrowingHandler = (createHandler
          forMethod GET
          forPath (root / "exception")
          requiringParameters List()
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      throw new Exception("BUM");
    }
  })

  val bodyParametersEchoHandler = (createHandler
          forMethod POST
          forPath (root / "body")
          includingParametersFromBody ()
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      StringResponse("OK " + parameters)
    }
  })

  val statusParamsHandler = (createHandler
          forMethod GET
          forPath (root / "echo" / "params")
          running new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      StringResponse("OK " + parameters, 401)
    }
  })

  val httpClient: HttpClient = new DefaultHttpClient()

  testWithServer(echoParamsHandler :: Nil, "should echo parameters")((server: RestServer) => {
    val action = new HttpGet(TestHost+"/echo/params?param1=z&param2=x")
    val response = httpClient.execute(action)

    val responseString = EntityUtils.toString(response.getEntity)

    responseString must include ("param1 -> z")
    responseString must include ("param2 -> x")
  })

  testWithServer(echoParamsHandler :: Nil, "should return 404")((server: RestServer) => {
    val action = new HttpGet(TestHost+"/nohandler")
    val response = httpClient.execute(action)
    EntityUtils.toString(response.getEntity)

    response.getStatusLine.getStatusCode must be (404)
  })

  testWithServer(exceptionThrowingHandler :: Nil, "should handle exceptions")((server: RestServer) => {
    val action = new HttpGet(TestHost+"/exception")
    val response = httpClient.execute(action)
    EntityUtils.toString(response.getEntity)

    response.getStatusLine.getStatusCode must be (500)
  })

  testWithServer(echoParamsHandler :: echo2ParamsHandler :: Nil, "should run appropriate handler")((server: RestServer) => {
    val action1 = new HttpGet(TestHost+"/echo/params?param1=z&param2=x")
    val response1 = httpClient.execute(action1)
    val responseString1 = EntityUtils.toString(response1.getEntity)

    val action2 = new HttpPost(TestHost+"/echo2/params2?a=190&b=222")
    val response2 = httpClient.execute(action2)
    val responseString2 = EntityUtils.toString(response2.getEntity)

    responseString1 must include ("OK")
    responseString1 must include ("param1 -> z")
    responseString1 must include ("param2 -> x")

    responseString2 must include ("KO")
    responseString2 must include ("a -> 190")
    responseString2 must include ("b -> 222")
  })

  testWithServer(bodyParametersEchoHandler :: Nil, "should include body parameters")((server: RestServer) => {
    val action = new HttpPost(TestHost+"/body")
    action.setEntity(new StringEntity("param1=aa&param2=bb"))

    val response = httpClient.execute(action)

    val responseString = EntityUtils.toString(response.getEntity)

    responseString must include ("param1 -> aa")
    responseString must include ("param2 -> bb")
  })

  testWithServer(statusParamsHandler :: Nil, "should return specified status code")((server: RestServer) => {
    val action = new HttpGet(TestHost+"/echo/params")
    val response = httpClient.execute(action)

    EntityUtils.toString(response.getEntity)

    response.getStatusLine.getStatusCode must be (401)
  })

  def testWithServer(handlers: List[CheckingRequestHandlerWrapper], name: String)(block: RestServer => Unit) {
    test(name) {
      val server = RestServer.start(handlers, new InetSocketAddress(TestPort))

      try {
        block(server)
      } finally {
        server.stop()
      }
    }
  }

  override protected def afterAll() {
    httpClient.getConnectionManager.shutdown()
  }
}