package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import org.elasticmq.rest.sqs.ActorSystemModule
import akka.http.scaladsl.server.Directives._

import scala.util.{Failure, Success}

trait FutureDirectives {
  this: ExceptionDirectives with ActorSystemModule =>

  implicit def futureRouteToRoute(futureRoute: Future[Route]): Route =
    onComplete(futureRoute) {
      case Success(r) => r
      case Failure(f) => exceptionHandlerPF(f)
    }
}
