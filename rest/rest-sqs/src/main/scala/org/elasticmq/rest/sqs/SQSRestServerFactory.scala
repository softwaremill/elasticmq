package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpMethod}
import org.elasticmq.rest.{StringResponse, RequestHandlerLogic, RestServer}
import org.elasticmq.{MillisVisibilityTimeout, Queue, Client}

class SQSRestServerFactory(client: Client) {
  val createQueueHandler = (createHandler
            forMethod HttpMethod.GET
            forPath (root / "test" / "me")
            requiringQueryParameters List("param1")
            running (new RequestHandlerLogic() {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        client.queueClient.createQueue(Queue("test", MillisVisibilityTimeout(1000L)))
        StringResponse("OK", "text/plain")
      }
    }))

  def start(port: Int): RestServer = {
    RestServer.start(createQueueHandler :: Nil, port)
  }
}