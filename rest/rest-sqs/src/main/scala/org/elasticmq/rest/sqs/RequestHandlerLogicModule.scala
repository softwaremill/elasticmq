package org.elasticmq.rest.sqs

import org.jboss.netty.handler.codec.http.HttpRequest

import org.elasticmq.rest.{StringResponse, RequestHandlerLogic}

import Constants._
import xml.Elem
import org.elasticmq.{ElasticMQException, Queue}

trait RequestHandlerLogicModule { this: ClientModule =>
  def logicWithQueue(body: (Queue, HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(QueueNameParameter)
        val version = parameters(VersionParameter)
        val namespace = namespaceFor(version)
        val queue = queueFor(queueName)
        body(queue, request, parameters) % namespace
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  def logicWithQueueName(body: (String, HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val queueName = parameters(QueueNameParameter)
        val version = parameters(VersionParameter)
        val namespace = namespaceFor(version)
        body(queueName, request, parameters) % namespace
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  def logic(body: (HttpRequest, Map[String, String]) => Elem): RequestHandlerLogic = {
    class TheLogic extends RequestHandlerLogic {
      def handle(request: HttpRequest, parameters: Map[String, String]) = {
        val version = parameters(VersionParameter)
        val namespace = namespaceFor(version)
        body(request, parameters) % namespace
      }
    }

    new TheLogic with ExceptionRequestHandlingLogic
  }

  private implicit def elemToStringResponse(e: Elem): StringResponse = StringResponse(e.toString())

  private def namespaceFor(version: String) = {
    if (version == null || version.isEmpty) { version = SqsDefaultVersion }

    new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null)
  }

  private def queueFor(queueName: String) = {
    val queueOption = client.lookupQueue(queueName)

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
        case e: SQSException => handleSQSException(e)
        case e: ElasticMQException => handleSQSException(new SQSException(e.code, e.getMessage))
      }
    }

    private def handleSQSException(e: SQSException) = StringResponse(e.toXml(EmptyRequestId).toString(), e.httpStatusCode)
  }
}
