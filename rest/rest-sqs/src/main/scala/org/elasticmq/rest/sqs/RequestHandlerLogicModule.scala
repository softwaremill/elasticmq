package org.elasticmq.rest.sqs

import org.jboss.netty.handler.codec.http.HttpRequest

import org.elasticmq.rest.{StringResponse, RequestHandlerLogic}
import org.elasticmq.Queue

import Constants._
import xml.Elem

trait RequestHandlerLogicModule { this: ClientModule =>
  def logicWithQueue(body: (Queue, HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(QUEUE_NAME_PARAMETER)
        val queue = queueFor(queueName)
        body(queue, request, parameters) % SQS_NAMESPACE
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  def logicWithQueueName(body: (String, HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(QUEUE_NAME_PARAMETER)
        body(queueName, request, parameters) % SQS_NAMESPACE
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  def logic(body: (HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        body(request, parameters) % SQS_NAMESPACE
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  private implicit def elemToStringResponse(e: Elem): StringResponse = StringResponse(e.toString())

  private def queueFor(queueName: String) = {
    val queueOption = client.queueClient.lookupQueue(queueName)

    queueOption match {
      case Some(q) => q
      case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
    }
  }

  private trait ExceptionRequestHandlingLogic extends RequestHandlerLogic {
    abstract override def handle(request: HttpRequest, parameters: Map[String, String]) = {
      try {
        super.handle(request, parameters)
      } catch {
        case e: SQSException => StringResponse(e.toXml(EMPTY_REQUEST_ID).toString())
      }
    }
  }
}