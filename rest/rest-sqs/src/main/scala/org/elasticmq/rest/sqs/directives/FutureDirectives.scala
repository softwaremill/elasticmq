package org.elasticmq.rest.sqs.directives

import scala.concurrent.Future
import spray.routing._
import org.elasticmq.rest.sqs.ActorSystemModule

trait FutureDirectives {
  this: ExceptionDirectives with ActorSystemModule =>

  implicit def futureRouteToRoute(futureRoute: Future[Route]): Route = { ctx =>
    futureRoute.map { route =>
      (handleServerExceptions {
        route
      })(ctx)
    }.onFailure {
      exceptionHandlerPF.andThen(_.apply(ctx))
    }
  }
}
