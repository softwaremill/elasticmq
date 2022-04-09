package org.elasticmq.rest.sqs.directives

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server._
import org.elasticmq.QueueData
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs._

trait QueueDirectives {
  this: Directives
    with QueueManagerActorModule
    with ContextPathModule
    with ActorSystemModule
    with FutureDirectives
    with AnyParamDirectives =>

  def queueNameFromParams(p: AnyParams): Directive1[String] =
    p.requiredParam("QueueName")

  def queueDataFromParams(p: AnyParams)(body: QueueData => Route): Route = {
    queueNameFromParams(p) { queueName => queueActor(queueName, queueData(_, body)) }
  }

  def queueActorFromRequest(p: AnyParams)(body: ActorRef => Route): Route = {
    queueNameFromRequest(p) { queueName => queueActor(queueName, body) }
  }

  def queueActorAndDataFromRequest(p: AnyParams)(body: (ActorRef, QueueData) => Route): Route = {
    queueNameFromRequest(p) { queueName => queueActor(queueName, qa => queueData(qa, qd => body(qa, qd))) }
  }

  def queueActorAndNameFromRequest(p: AnyParams)(body: (ActorRef, String) => Route): Route = {
    queueNameFromRequest(p) { queueName => queueActor(queueName, qa => body(qa, queueName)) }
  }

  def queueActorAndDataFromQueueName(queueName: String)(body: (ActorRef, QueueData) => Route): Route = {
    queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
  }

  private val queueUrlParameter = "QueueUrl"

  private def queueUrlFromParams(p: AnyParams): Directive1[String] =
    p.requiredParam(queueUrlParameter)

  private val accountIdRegex = "[a-zA-Z0-9]+".r

  private def queueNameFromRequest(p: AnyParams)(body: String => Route): Route = {
    val pathDirective =
      if (contextPath.nonEmpty)
        pathPrefix(contextPath / accountIdRegex / Segment).tmap(_._2) |
          pathPrefix(contextPath / QueueUrlContext / Segment)
      else
        pathPrefix(accountIdRegex / Segment).tmap(_._2) |
          pathPrefix(QueueUrlContext / Segment)

    val queueNameDirective =
      checkOnlyOneSegmentInUri() |
        pathDirective |
        queueNameFromParams(p) |
        queueUrlFromParams(p).flatMap { queueUrl => getQueueNameFromQueueUrl(queueUrl) }

    queueNameDirective(body)
  }

  private def getQueueNameFromQueueUrl(queueUrl: String): Directive1[String] = {

    val matcher =
      if (contextPath.nonEmpty)
        separateOnSlashes(contextPath) / accountIdRegex / "[^/]+".r
      else
        Slash ~ accountIdRegex / "[^/]+".r

    matcher(Uri(queueUrl).path) match {
      case Matched(_, extractions) => provide(extractions._2): Directive1[String]
      case Unmatched =>
        reject(
          MalformedQueryParamRejection(
            queueUrlParameter,
            "Invalid queue url, the path should be /<accountId>/<queueName> where accountId must match " + accountIdRegex + " regex"
          )
        )
    }
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

  private def checkOnlyOneSegmentInUri(): Directive1[String] = {
    path(Segment).flatMap { _ =>
      reject(WrongURLFormatRejection("Provided only queueName instead of the full URL")): Directive1[String]
    }
  }
}

final case class WrongURLFormatRejection(fieldName: String) extends Rejection
