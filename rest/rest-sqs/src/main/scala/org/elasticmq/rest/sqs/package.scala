package org.elasticmq.rest

import akka.http.scaladsl.server.{Directive0, Directive1, MissingQueryParamRejection}
import akka.http.scaladsl.server.Directives._

package object sqs {
  type AnyParams = Map[String, String]

  implicit class RichAnyParam(p: AnyParams) {

    def action(requiredAction: Action.Value): Directive0 = {
      if (p.get("Action").contains(requiredAction.toString)) {
        pass
      } else {
        reject
      }
    }

    def requiredParam(n: String): Directive1[String] = {
      p.get(n) match {
        case Some(v) => provide(v)
        case None    => reject(MissingQueryParamRejection(n))
      }
    }
  }
}
