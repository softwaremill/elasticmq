package org.elasticmq.rest.sqs.directives

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import org.apache.pekko.http.scaladsl.server._
import org.elasticmq.QueueData
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{GetQueueData, LookupQueue}
import org.elasticmq.rest.sqs.Constants.QueueUrlParameter
import org.elasticmq.rest.sqs._
import org.elasticmq.rest.sqs.directives.QueueDirectives.AccountIdRegex

import scala.util.matching.Regex

trait QueueDirectives {
  this: Directives with QueueManagerActorModule with ContextPathModule with ActorSystemModule with FutureDirectives =>

  def queueActorFromUrl(queueUrl: String)(body: ActorRef => Route): Route =
    getQueueNameFromQueueUrl(queueUrl)(queueName => queueActor(queueName, body))

  def queueActorAndNameFromUrl(queueUrl: String)(body: (ActorRef, String) => Route): Route = {
    getQueueNameFromQueueUrl(queueUrl) { queueName => queueActor(queueName, qa => body(qa, queueName)) }
  }

  def queueActorAndDataFromQueueName(queueName: String)(body: (ActorRef, QueueData) => Route): Route = {
    queueActor(queueName, qa => queueData(qa, qd => body(qa, qd)))
  }

  def queueActorAndDataFromQueueUrl(queueUrl: String)(body: (ActorRef, QueueData) => Route): Route = {
    getQueueNameFromQueueUrl(queueUrl)(queueName => queueActor(queueName, qa => queueData(qa, qd => body(qa, qd))))
  }

  protected def getQueueNameFromQueueUrl(queueUrl: String): Directive1[String] = {

    val defaultMatcher =
      if (contextPath.nonEmpty) {
        val pathWithContext = separateOnSlashes(contextPath) / AccountIdRegex / "[^/]+".r
        Slash ~ pathWithContext | pathWithContext
      } else
        Slash ~ AccountIdRegex / "[^/]+".r

    val noAccountIdMatcher =
      if (contextPath.nonEmpty) {
        val pathWithContext = separateOnSlashes(contextPath) / "[^/]+".r
        Slash ~ pathWithContext | pathWithContext
      } else
        Slash ~ "[^/]+".r

    defaultMatcher(Uri(queueUrl).path) match {
      case Matched(_, (_, queueName)) => provide(queueName): Directive1[String]
      case Unmatched                  =>
        noAccountIdMatcher(Uri(queueUrl).path) match {
          case Matched(_, Tuple1(queueName)) => provide(queueName)
          case Unmatched                     =>
            reject(
              MalformedQueryParamRejection(
                QueueUrlParameter,
                "Invalid queue url, the path should be /<accountId>/<queueName> or /<queueName>"
              )
            )
        }
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
}

object QueueDirectives {
  val AccountIdRegex: Regex = "[a-zA-Z0-9]+".r
}
