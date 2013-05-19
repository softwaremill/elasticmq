package org.elasticmq.rest.sqs.directives

import spray.routing._
import akka.actor.ActorRef
import org.elasticmq.data.QueueData
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule, SQSException}
import org.elasticmq.actor.reply._

trait QueueDirectives {
  this: Directives with QueueManagerActorModule with ActorSystemModule with FutureDirectives =>

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

  def queueActorAndNameFromPath(body: (ActorRef, String) => Route) = {
    queueNameFromPath { queueName =>
      queueActor(queueName, qa => body(qa, queueName))
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
