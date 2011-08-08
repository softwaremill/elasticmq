package org.elasticmq.rest.sqs

import org.jboss.netty.handler.codec.http.HttpRequest
import org.elasticmq.rest.{StringResponse, RequestHandlerLogic}

import SQSConstants._

trait SQSLogic extends RequestHandlerLogic {
  abstract override def handle(request: HttpRequest, parameters: Map[String, String]) = {
    try {
      super.handle(request, parameters)
    } catch {
      case e: SQSException => StringResponse(e.toXml(emptyRequestId).toString())
    }
  }
}

object SQSLogic {
  def logic(body: (String, HttpRequest, Map[String, String]) => StringResponse): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(queueNameParameter)
        body(queueName, request, parameters)
      }
    }

    new TheLogic with SQSLogic
  }
}