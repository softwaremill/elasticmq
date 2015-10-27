package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{MissingFormFieldRejection, Directives, Route}
import akka.actor.ActorRef
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs._
import org.elasticmq.actor.reply._
import org.elasticmq.QueueData
import org.elasticmq.rest.sqs.Constants._

trait QueueDirectives {
  this: Directives with QueueManagerActorModule with ActorSystemModule with FutureDirectives with AnyParamDirectives =>

  def queueNameFromParams(p: AnyParams)(body: String => Route) = {
    p.requiredParam("QueueName") { queueName =>
      body(queueName)
    }
  }

  def queueDataFromParams(p: AnyParams)(body: QueueData => Route) = {
    queueNameFromParams(p) { queueName =>
      queueActor(queueName, queueData(_, body))
    }
  }

  def queueActorFromRequest(p: AnyParams)(body: ActorRef => Route) = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, body)
    }
  }

  def queueActorAndDataFromRequest(p: AnyParams)(body: (ActorRef, QueueData) => Route) = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
    }
  }

  def queueActorAndNameFromRequest(p: AnyParams)(body: (ActorRef, String) => Route) = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, qa => body(qa, queueName))
    }
  }

  private val queueUrlParameter = "QueueUrl"

  private def queueUrlFromParams(p: AnyParams)(body: String => Route) = {
    p.requiredParam(queueUrlParameter) { queueUrl =>
      body(queueUrl)
    }
  }

  private val lastPathSegment = ("^[^/]*//[^/]*/" + QueueUrlContext + "/([^/]+)$").r

  private def queueNameFromRequest(p: AnyParams)(body: String => Route) = {
    path(QueueUrlContext / Segment) { queueName =>
      body(queueName)
    } ~
    queueNameFromParams(p) { queueName =>
      body(queueName)
    } ~
    queueUrlFromParams(p) { queueUrl =>
      lastPathSegment.findFirstMatchIn(queueUrl).map(_.group(1)) match {
        case Some(queueName) => body(queueName)
        case None => _.reject(MissingFormFieldRejection(queueUrlParameter))
      }
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
}
