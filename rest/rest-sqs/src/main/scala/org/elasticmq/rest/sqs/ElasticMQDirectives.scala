package org.elasticmq.rest.sqs

import spray.routing._
import scala.xml.{Null, UnprefixedAttribute, Elem}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.directives.AnyParamDirectives
import shapeless.HNil
import spray.http.StatusCode._
import akka.actor.ActorRef
import org.elasticmq.data.QueueData
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.actor.reply._
import scala.concurrent.Future
import org.elasticmq.ElasticMQException
import spray.http.StatusCodes
import com.typesafe.scalalogging.slf4j.Logging

trait ElasticMQDirectives extends Directives with AnyParamDirectives with QueueManagerActorModule with ActorSystemModule with Logging {

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

  val exceptionHandler = ExceptionHandler.fromPF {
    case e: SQSException => handleSQSException(e)
    case e: ElasticMQException => handleSQSException(new SQSException(e.code, e.getMessage))
    case e: Exception => {
      logger.error("Exception when running routes", e)
      _.complete(StatusCodes.InternalServerError)
    }
  }

  def handleServerExceptions = handleExceptions(exceptionHandler)

  def namespace(route: UnprefixedAttribute => Route): Route = parameter("Version"?) { versionOpt =>
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

  def queueNameFromParams(body: String => Route) = {
    anyParam("QueueName") { queueName =>
      body(queueName)
    }
  }

  def queueActorFromParams(body: ActorRef => Route) = {
    queueNameFromParams { queueName =>
      queueActor(queueName, body)
    }
  }

  def queueDataFromParams(body: QueueData => Route) = {
    queueNameFromParams { queueName =>
      queueActor(queueName, queueData(_, body))
    }
  }

  def rootPath(body: Route) = {
    path("") {
      body
    }
  }

  def queueNameFromPath(body: String => Route) = {
    path("queue" / Segment) { queueName =>
      body(queueName)
    }
  }

  def queueActorFromPath(body: ActorRef => Route) = {
    queueNameFromPath { queueName =>
      queueActor(queueName, body)
    }
  }

  def queueDataFromPath(body: QueueData => Route) = {
    queueNameFromPath { queueName =>
      queueActor(queueName, queueData(_, body))
    }
  }

  def queueActorAndDataFromPath(body: (ActorRef, QueueData) => Route) = {
    queueNameFromPath { queueName =>
      queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
    }
  }

  private def queueActor(queueName: String, body: ActorRef => Route): Route = {
    for {
      lookupResult <- queueManagerActor ? LookupQueue(queueName)
    } yield {
      lookupResult match {
        case Some(a) => body(a)
        case None => throw new SQSException("AWS.SimpleQueueService.NonExistentQueue")
      }
    }
  }

  private def queueData(queueActor: ActorRef, body: QueueData => Route): Route = {
    for {
      queueData <- queueActor ? GetQueueData()
    } yield {
      body(queueData)
    }
  }

  implicit def futureRouteToRoute(futureRoute: Future[Route]): Route = { ctx =>
    futureRoute.map { route =>
      (handleServerExceptions {
        route
      })(ctx)
    }
  }
}
