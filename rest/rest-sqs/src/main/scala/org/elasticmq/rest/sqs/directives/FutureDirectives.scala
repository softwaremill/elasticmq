package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.Future
import org.elasticmq.rest.sqs.ActorSystemModule
import org.apache.pekko.http.scaladsl.server.Directives._

import scala.util.{Failure, Success}

trait FutureDirectives {
  this: ExceptionDirectives with ActorSystemModule with AWSProtocolDirectives =>

  implicit def futureRouteToRoute(futureRoute: Future[Route]): Route = {
    extractProtocol { protocol =>
      onComplete(futureRoute) {
        case Success(r) => r
        case Failure(f) => exceptionHandlerPF(protocol)(f)
      }
    }
  }
}
