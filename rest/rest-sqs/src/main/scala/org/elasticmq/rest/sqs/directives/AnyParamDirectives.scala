package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.model.{FormData, HttpRequest}
import akka.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import akka.stream.Materializer
import org.elasticmq.rest.sqs.{AWSProtocol, ContextPathModule}
import org.elasticmq.rest.sqs.Constants.{QueueNameParameter, QueueUrlParameter}
import org.elasticmq.rest.sqs.directives.QueueDirectives.AccountIdRegex
import org.elasticmq.rest.sqs.model.{JsonData, RequestPayload}

trait AnyParamDirectives {
  this: Directives with ContextPathModule =>

  private def formDataOrEmpty =
    entity(as[FormData]).recoverPF {
      // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
      case Seq(UnsupportedRequestContentTypeRejection(_)) =>
        provide(FormData.Empty)
    }

  private val actionHeaderPattern = """^AmazonSQS\.(\w+)$""".r
  private def extractActionFromHeader(request: HttpRequest) =
    request.headers
      .find(_.name() == "X-Amz-Target")
      .flatMap(
        _.value() match {
          case actionHeaderPattern(action) => Some(action)
          case _                           => None
        }
      ).getOrElse(throw new IllegalArgumentException("Couldn't find header X-Amz-Target"))
  private def extractAwsXRayTracingHeader(request: HttpRequest): Option[String] = {
    request.headers
      .find(_.name().equalsIgnoreCase("X-Amzn-Trace-Id"))
      .map(_.value())
  }

  def extractQueueNameAndUrlFromRequest(body: Map[String, String] => Route): Route = {

    val pathDirective = {
      if (contextPath.nonEmpty)
        pathPrefix(contextPath / AccountIdRegex / Segment.?).tmap(_._2) | provide(Option.empty[String])
      else {
        pathPrefix(AccountIdRegex / Segment.?).tmap(_._2) |
          provide(Option.empty[String])
      }
    }.flatMap {
      case Some(name) =>
        extractUri.flatMap { uri =>
          provide(Map(QueueNameParameter -> name, QueueUrlParameter -> uri.toString))
        }
      case None => provide(Map.empty[String, String])
    }

    pathDirective(body)
  }

  def anyParamsMap(protocol: AWSProtocol)(body: RequestPayload => Route) = {

    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` =>
        extractRequest { request =>
          entity(as[JsonData]) { json =>
            body(
              RequestPayload.JsonParams(
                json.payload,
                extractActionFromHeader(request),
                extractAwsXRayTracingHeader(request)
              )
            )
          }
        }
      case _ =>
        parameterMap { queryParameters =>
          extractRequest { request =>
            formDataOrEmpty { fd =>
              extractQueueNameAndUrlFromRequest { queueNameAndUrl =>
                val params = queryParameters ++ fd.fields.toMap ++ queueNameAndUrl

                body(
                  RequestPayload.QueryParams(
                    params.filter(_._1 != ""),
                    extractAwsXRayTracingHeader(request)
                  )
                )
              }
            }
          }
        }
    }
  }

  implicit def materializer: Materializer
}
