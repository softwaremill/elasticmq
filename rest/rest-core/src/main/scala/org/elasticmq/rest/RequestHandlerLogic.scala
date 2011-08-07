package org.elasticmq.rest

import org.jboss.netty.handler.codec.http.{QueryStringDecoder, HttpMethod, HttpRequest}
import java.nio.charset.Charset

trait RequestHandlerLogic {
  def handle(request: HttpRequest, parameters: Map[String, String]): StringResponse
}

case class StringResponse(content: String, contentType: String)

object StringResponse {
  def apply(content: String): StringResponse = StringResponse(content, "text/plain")
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
  def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]): CheckerResult
}

trait CheckingRequestHandlerWrapper extends CanHandleRequestChecker {
  def clientHandler: RequestHandlerLogic
}

/**
 * Usage example ([] - optional):
 *
 * import RequestHandlerBuilder.createHandler
 * val handler = (createHandler
 *  forMethod GET
 *  forPath (root / "abc" / %("abc") / "def")
 *  [includingParametersFromBody ()]
 *  [requiringParameters "a", "b", "c"]
 *  [requiringParameterValues "a"->"1", "b"->"2"]
 *  running myRequestHandler)
 */
object RequestHandlerBuilder {
  def createHandler = new MethodSpecifier

  import scala.collection.JavaConversions._

  private def javaMultiValueParamMapToFlatMap(javaMap: java.util.Map[String, java.util.List[String]]) = {
    javaMap.map { case (k, v) => (k, v.get(0)) }.toMap
  }

  class MethodSpecifier {
    def forMethod(method: HttpMethod) = {
      object MethodChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) =
          if (request.getMethod == method) Some(Map()) else None
      }

      new PathSpecifier(MethodChecker :: Nil)
    }
  }

  class PathSpecifier(checkers: Seq[CanHandleRequestChecker]) { outer =>
    def forPath(restPath: RestPath) = {
      object PathChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) = {
          val queryStringDecoder = new QueryStringDecoder(request.getUri)

          restPath.matchAndExtract(queryStringDecoder.getPath).map(
            _ ++ javaMultiValueParamMapToFlatMap(queryStringDecoder.getParameters)
          )
        }
      }

      new IncludingParametersFromBodySpecifier {
        val checkers = outer.checkers ++ Seq(PathChecker)
      }
    }
  }

  trait WithCheckers {
    val checkers: Seq[CanHandleRequestChecker]
  }

  trait RequiringParameterValuesSpecifier extends RunningSpecifier with WithCheckers { outer =>
    def requiringParameterValues(paramValues: Map[String, String]) = {
      object RequiredParameterValuesChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) = {
          if (paramValues.forall(pv => parametersSoFar.exists(_ == pv)))
            Some(Map())
          else
            None
        }
      }

      new RunningSpecifier {
        val checkers = outer.checkers ++ Seq(RequiredParameterValuesChecker)
      }
    }
  }

  trait RequiredParametersSpecifier extends RequiringParameterValuesSpecifier with RunningSpecifier
    with WithCheckers { outer =>
    def requiringParameters(paramNames: Seq[String]) = {
      object ParametersChecker extends CanHandleRequestChecker {
        def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) = {
          val parameterNames = parametersSoFar.keySet
          if (paramNames.forall(parameterNames.contains(_)))
            Some(Map())
          else
            None
        }
      }

      new IncludingParametersFromBodySpecifier() {
        val checkers = outer.checkers ++ Seq(ParametersChecker)
      }
    }
  }

  trait IncludingParametersFromBodySpecifier extends RequiringParameterValuesSpecifier
    with RequiredParametersSpecifier with RunningSpecifier with WithCheckers { outer =>
    def includingParametersFromBody() = {
      object BodyChecker extends CanHandleRequestChecker {
         def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) = {
           val body = request.getContent.toString(Charset.defaultCharset())
           val queryStringDecoder = new QueryStringDecoder("/path?" + body)
           Some(javaMultiValueParamMapToFlatMap(queryStringDecoder.getParameters))
         }
      }

      new RequiredParametersSpecifier {
        val checkers = outer.checkers ++ Seq(BodyChecker)
      }
    }
  }

  trait RunningSpecifier extends WithCheckers {
    def running(theClientHandler: RequestHandlerLogic) = new CheckingRequestHandlerWrapper {
      def canHandle(request: HttpRequest, parametersSoFar: Map[String, String]) = {
        def doCanHandle(checkersLeft: Seq[CanHandleRequestChecker], acc: Map[String, String]): CheckerResult = {
          if (checkersLeft.isEmpty) {
            Some(acc)
          } else {
            checkersLeft.head.canHandle(request, acc) match {
              case Some(m) => doCanHandle(checkersLeft.tail, acc ++ m)
              case None => None
            }
          }
        }

        doCanHandle(checkers, parametersSoFar)
      }

      val clientHandler = theClientHandler
    }
  }
}