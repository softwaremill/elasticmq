package org.elasticmq.rest.sqs.directives

import akka.actor.ActorRef
import akka.http.scaladsl.server.{Directive1, Directives, MissingFormFieldRejection, Route}
import org.elasticmq.QueueData
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs._

trait QueueDirectives {
  this: Directives with QueueManagerActorModule with ActorSystemModule with FutureDirectives with AnyParamDirectives =>

  def queueNameFromParams(p: AnyParams): Directive1[String] =
    p.requiredParam("QueueName")

  def queueDataFromParams(p: AnyParams)(body: QueueData => Route): Route = {
    queueNameFromParams(p) { queueName =>
      queueActor(queueName, queueData(_, body))
    }
  }

  def queueActorFromRequest(p: AnyParams)(body: ActorRef => Route): Route = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, body)
    }
  }

  def queueActorAndDataFromRequest(p: AnyParams)(body: (ActorRef, QueueData) => Route): Route = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
    }
  }

  def queueActorAndNameFromRequest(p: AnyParams)(body: (ActorRef, String) => Route): Route = {
    queueNameFromRequest(p) { queueName =>
      queueActor(queueName, qa => body(qa, queueName))
    }
  }

  private val queueUrlParameter = "QueueUrl"

  private def queueUrlFromParams(p: AnyParams): Directive1[String] =
    p.requiredParam(queueUrlParameter)

  private val accountId = "[a-zA-Z0-9]{12}"
  private val lastPathSegment =
    ("^[^/]*//[^/]*/(" + accountId + "|" + QueueUrlContext + ")/([^/]+)$").r

  private def queueNameFromRequest(p: AnyParams)(body: String => Route): Route = {
    val queueNameDirective =
      pathPrefix(accountId.r / Segment).tmap(_._2) |
        pathPrefix(QueueUrlContext / Segment) |
        queueNameFromParams(p) |
        queueUrlFromParams(p).flatMap { queueUrl =>
          lastPathSegment.findFirstMatchIn(queueUrl).map(_.group(2)) match {
            case Some(queueName) => provide(queueName)
            case None            => reject(MissingFormFieldRejection(queueUrlParameter)): Directive1[String]
          }
        }

    queueNameDirective(body)
  }

  private def queueActor(queueName: String, body: ActorRef => Route): Route = {
    for {
      lookupResult <- queueManagerActor ? LookupQueue(queueName)
    } yield {
      lookupResult match {
        case Some(a) => body(a)
        case None    => throw SQSException.nonExistentQueue
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
