package org.elasticmq.rest.sqs

import org.jboss.netty.handler.codec.http.HttpRequest
import org.elasticmq.rest.{StringResponse, RequestHandlerLogic}

import SQSConstants._

trait SQSRequestHandlingLogic extends RequestHandlerLogic {
  abstract override def handle(request: HttpRequest, parameters: Map[String, String]) = {
    try {
      super.handle(request, parameters)
    } catch {
      case e: SQSException => StringResponse(e.toXml(emptyRequestId).toString())
    }
  }
}

object SQSRequestHandlingLogic {
  def logic(body: (String, HttpRequest, Map[String, String]) => StringResponse): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(queueNameParameter)
        body(queueName, request, parameters)
      }
    }

    new TheLogic with SQSRequestHandlingLogic
  }

  def logic(body: (HttpRequest, Map[String, String]) => StringResponse): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        body(request, parameters)
      }
    }

    new TheLogic with SQSRequestHandlingLogic
  }
}