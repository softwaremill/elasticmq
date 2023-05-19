package org.elasticmq.rest.sqs.model

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.elasticmq.rest.sqs.{Action, FlatParamsReader}
import org.elasticmq.rest.sqs.model.RequestPayload.QueryParams
import spray.json.{JsObject, RootJsonFormat}

sealed trait RequestPayload {
  def action(requiredAction: Action.Value): Directive0

  def maybeAction: Option[String]

  def as[A: RootJsonFormat: FlatParamsReader]: A = this match {
    case json: RequestPayload.JsonParams => json.readAs[A]
    case query: RequestPayload.QueryParams => query.readAs[A]
  }
}

object RequestPayload {
  final case class QueryParams(params: Map[String, String], xRayTracingHeader: Option[String] = None) extends RequestPayload {
    def readAs[A: FlatParamsReader](implicit fpr: FlatParamsReader[A]) = fpr.read(params)

    override def maybeAction: Option[String] = params.get("Action")

    override def action(requiredAction: Action.Value): Directive0 =  if (params.get("Action").contains(requiredAction.toString)) pass else reject
  }

  final case class JsonParams(params: JsObject, action: String, xRayTracingHeader: Option[String]) extends RequestPayload {
    def readAs[A: RootJsonFormat](implicit fpr: FlatParamsReader[A]) = params.convertTo[A]

    override def maybeAction: Option[String] = Some(action)

    override def action(requiredAction: Action.Value): Directive0 =  if (requiredAction.toString == action) pass else reject
  }
}
