package org.elasticmq.rest.sqs

import spray.routing._
import scala.xml.{Null, UnprefixedAttribute, Elem}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.AnyParamDirectives
import shapeless.HNil
import org.elasticmq.Queue
import spray.http.StatusCode._

trait ElasticMQDirectives extends Directives with AnyParamDirectives with ClientModule {

  def respondWith(elem: Elem): Route = namespace { ns =>
    (ctx: RequestContext) => {
      val result = elem % ns
      ctx.complete(result.toString())
    }
  }

  def respondWith(statusCode: Int)(elem: Elem): Route = namespace { ns =>
    (ctx: RequestContext) => {
      val result = elem % ns
      ctx.complete(statusCode, result.toString())
    }
  }

  def handleSQSException(e: SQSException) = {
    respondWith(e.httpStatusCode) {
      e.toXml(EmptyRequestId)
    }
  }

  def namespace(route: UnprefixedAttribute => Route): Route = parameter(VersionParameter?) { versionOpt =>
    val version = versionOpt match {
      case Some(v) if !v.isEmpty => v
      case _ => SqsDefaultVersion
    }

    route(new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/%s/".format(version), Null))
  }

  def action(requiredActionName: String): Directive[HNil] = {
    anyParam("Action").flatMap { actionName =>
      if (actionName == requiredActionName) {
        pass
      } else {
        reject
      }
    }
  }

  def withQueueName(body: String => Route) = {
    anyParam("QueueName") { queueName =>
      body(queueName)
    }
  }

  def withQueue(body: Queue => Route) = {
    withQueueName { queueName =>
      val queue = queueFor(queueName)
      body(queue)
    }
  }

  def rootPath(body: Route) = {
    path("") {
      body
    }
  }

  def queueNamePath(body: String => Route) = {
    path("queue" / PathElement) { queueName =>
      body(queueName)
    }
  }

  def queuePath(body: Queue => Route) = {
    queueNamePath { queueName =>
      val queue = queueFor(queueName)
      body(queue)
    }
  }

  private def queueFor(queueName: String) = {
    val queueOption = client.lookupQueue(queueName)

    queueOption match {
      case Some(q) => q
      case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
    }
  }
}
