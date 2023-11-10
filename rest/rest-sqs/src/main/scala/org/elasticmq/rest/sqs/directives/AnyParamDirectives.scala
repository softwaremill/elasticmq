package org.elasticmq.rest.sqs.directives

import org.apache.pekko.http.scaladsl.model.{FormData, HttpRequest}
import org.apache.pekko.http.scaladsl.server.{Directives, Route, UnsupportedRequestContentTypeRejection}
import org.apache.pekko.stream.Materializer
import org.elasticmq.rest.sqs.{AWSProtocol, ContextPathModule, QueueURLModule}
import org.elasticmq.rest.sqs.Constants.{QueueNameParameter, QueueUrlParameter}
import org.elasticmq.rest.sqs.directives.QueueDirectives.AccountIdRegex
import org.elasticmq.rest.sqs.model.{JsonData, RequestPayload}

trait AnyParamDirectives {
  this: Directives with ContextPathModule with QueueURLModule =>

  private def formDataOrEmpty =
    entity(as[FormData]).recoverPF {
      // #68: some clients don't set the body as application/x-www-form-urlencoded, e.g. perl
      case Seq(UnsupportedRequestContentTypeRejection(_)) =>
        provide(FormData.Empty)
    }

  private val actionHeaderPattern = """^AmazonSQS\.(\w+)$""".r
  private def extractActionFromHeader =
    extractRequest.map(request =>
      request.headers
        .find(_.name().equalsIgnoreCase("X-Amz-Target"))
        .flatMap(
          _.value() match {
            case actionHeaderPattern(action) => Some(action)
            case _                           => None
          }
        )
        .getOrElse(throw new IllegalArgumentException("Couldn't find header X-Amz-Target"))
    )

  private def extractAwsXRayTracingHeader =
    extractRequest.map(
      _.headers
        .find(_.name().equalsIgnoreCase("X-Amzn-Trace-Id"))
        .map(_.value())
    )

  private def extractQueueNameAndUrlFromRequest(body: Map[String, String] => Route): Route = {

    val pathDirective = {
      if (contextPath.nonEmpty) {
        pathPrefix(contextPath / AccountIdRegex / Segment).tmap(t => Option(t._2)) |
          pathPrefix(contextPath / Segment).map(Option(_)) |
          provide(Option.empty[String])
      } else {
        pathPrefix(AccountIdRegex / Segment).tmap(t => Option(t._2)) |
          pathPrefix(Segment).map(Option(_)) |
          provide(Option.empty[String])
      }
    }.flatMap {
      case Some(name) =>
        queueURL(name).map { url =>
          Map(QueueNameParameter -> name, QueueUrlParameter -> url)
        }
      case None => provide(Map.empty[String, String])
    }

    pathDirective(body)
  }

  def anyParamsMap(protocol: AWSProtocol)(body: RequestPayload => Route) = {

    protocol match {
      case AWSProtocol.`AWSJsonProtocol1.0` =>
        extractActionFromHeader { action =>
          extractAwsXRayTracingHeader { tracingHeader =>
            entity(as[JsonData]) { json =>
              body(
                RequestPayload.JsonParams(
                  json.payload,
                  action,
                  tracingHeader
                )
              )
            }
          }
        }
      case _ =>
        parameterMap { queryParameters =>
          extractAwsXRayTracingHeader { tracingHeader =>
            formDataOrEmpty { fd =>
              extractQueueNameAndUrlFromRequest { queueNameAndUrl =>
                val params = queryParameters ++ fd.fields.toMap ++ queueNameAndUrl
                body(
                  RequestPayload.QueryParams(
                    params.filter(_._1 != ""),
                    tracingHeader
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
