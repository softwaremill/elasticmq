package org.elasticmq.rest.impl

import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpRequest}
import java.net.URI

trait RequestHandlerLogic {
  def handle(request: HttpRequest, parameters: Map[String, String], channel: Channel)
}

object CanHandleRequestChecker {
  type CheckerResult = Option[Map[String, String]]
}

import CanHandleRequestChecker.CheckerResult

trait CanHandleRequestChecker {
  /**
   * @return If the request can be handled, a map of parameters to pass to the {@link RequestHandler}.
   * If the request cannot be handled, {@code None}.
   */
  def canHandle(request: HttpRequest, uri: URI): CheckerResult
}

trait CheckingRequestHandlerWrapper extends CanHandleRequestChecker {
  def handler: RequestHandlerLogic
}

/**
 * Usage example:
 *
 * import RequestHandlerBuilder.createHandler
 * val handler = (createHandler
 *  forMethod GET
 *  forPath /"abc"/:abc/"def"
 *  requiringQueryParameters "a", "b", "c"
 *  running myRequestHandler)
 */
object RequestHandlerBuilder {
  def createHandler = new MethodSpecifier

  class MethodSpecifier {
    def forMethod(method: HttpMethod) = {
      object MethodChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, uri: URI) = if (request.getMethod == method) Some(Map()) else None
      }

      new PathSpecifier(MethodChecker :: Nil)
    }
  }

  class PathSpecifier(checkers: Seq[CanHandleRequestChecker]) {
    def forPath(path: String) = {
      object PathChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, uri: URI) = if (uri.getPath == path) Some(Map()) else None
      }

      new RequiredParametersSpecifier(checkers ++ Seq(PathChecker))
    }
  }

  class RequiredParametersSpecifier(checkers: Seq[CanHandleRequestChecker]) {
    def requiringQueryParameters(paramNames: Seq[String]) = {
      object ParametersChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, uri: URI) = {
          // TODO: parse query, check
          Some(Map())
        }
      }

      new RunningSpecifier(checkers ++ Seq(ParametersChecker))
    }
  }

  class RunningSpecifier(checkers: Seq[CanHandleRequestChecker]) {
    def running(clientHandler: RequestHandlerLogic) = new CheckingRequestHandlerWrapper {
      def canHandle(request: HttpRequest, uri: URI) = {
        def doCanHandle(checkersLeft: Seq[CanHandleRequestChecker], acc: Map[String, String]): CheckerResult = {
          if (checkersLeft.isEmpty) {
            Some(acc)
          } else {
            checkersLeft.head.canHandle(request, uri) match {
              case Some(m) => doCanHandle(checkersLeft.tail, acc ++ m)
              case None => None
            }
          }
        }

        doCanHandle(checkers, Map())
      }

      val handler = clientHandler
    }
  }
}