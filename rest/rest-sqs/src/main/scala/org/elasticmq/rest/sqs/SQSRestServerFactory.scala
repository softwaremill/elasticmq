package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpMethod}
import org.elasticmq.rest.{StringResponse, RequestHandlerLogic, RestServer}
import org.elasticmq.{MillisVisibilityTimeout, Queue, Client}

import HttpMethod._

class SQSRestServerFactory(client: Client) {
  val queueNameParameter = "QueueName"
  val actionParameter = "Action"

  val createQueueAction = actionParameter -> "CreateQueue"

  val createQueueLogic = new RequestHandlerLogic() {
    def handle(request: HttpRequest, parameters: Map[String, String]) = {
      client.queueClient.createQueue(Queue(parameters(queueNameParameter), MillisVisibilityTimeout(1000L)))

      val response =
        <CreateQueueResponse
          xmlns="http://queue.amazonaws.com/doc/2009-02-01/"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:type="CreateQueueResponse">
          <CreateQueueResult>
            <QueueUrl>
              http://sqs.us-east-1.amazonaws.com/123456789012/testQueue
            </QueueUrl>
          </CreateQueueResult>
          <ResponseMetadata>
            <RequestId>
              7a62c49f-347e-4fc4-9331-6e8e7a96aa73
            </RequestId>
          </ResponseMetadata>
        </CreateQueueResponse>

      StringResponse(response.toString(), "text/plain")
    }
  }

  val createQueueGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameters List(queueNameParameter)
            requiringParameterValues Map(createQueueAction)
            running createQueueLogic)

  val createQueuePostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody ()
            requiringParameters List(queueNameParameter)
            requiringParameterValues Map(createQueueAction)
            running createQueueLogic)

  def start(port: Int): RestServer = {
    RestServer.start(
      createQueueGetHandler :: createQueuePostHandler ::
              Nil, port)
  }
}