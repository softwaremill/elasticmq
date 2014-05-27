package org.elasticmq.rest.sqs.directives

import spray.routing._
import akka.actor.ActorRef
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule, SQSException}
import org.elasticmq.actor.reply._
import org.elasticmq.QueueData
import org.elasticmq.rest.sqs.Constants._

trait QueueDirectives {
  this: Directives with QueueManagerActorModule with ActorSystemModule with FutureDirectives =>

  def queueNameFromParams(body: String => Route) = {
    anyParam("QueueName") { queueName =>
      body(queueName)
    }
  }

  def queueDataFromParams(body: QueueData => Route) = {
    queueNameFromParams { queueName =>
      queueActor(queueName, queueData(_, body))
    }
  }

  def queueActorFromRequest(body: ActorRef => Route) = {
    queueNameFromRequest { queueName =>
      queueActor(queueName, body)
    }
  }

  def queueActorAndDataFromRequest(body: (ActorRef, QueueData) => Route) = {
    queueNameFromRequest { queueName =>
      queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
    }
  }

  def queueActorAndNameFromRequest(body: (ActorRef, String) => Route) = {
    queueNameFromRequest { queueName =>
      queueActor(queueName, qa => body(qa, queueName))
    }
  }

  private val queueUrlParameter = "QueueUrl"

  private def queueUrlFromParams(body: String => Route) = {
    anyParam(queueUrlParameter) { queueUrl =>
      body(queueUrl)
    }
  }

  private val lastPathSegment = ("^[^/]*//[^/]*/" + QueueUrlContext + "/([^/]+)$").r

  private def queueNameFromRequest(body: String => Route) = {
    path(QueueUrlContext / Segment) { queueName =>
      body(queueName)
    } ~
    queueNameFromParams { queueName =>
      body(queueName)
    } ~
    queueUrlFromParams { queueUrl =>
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
