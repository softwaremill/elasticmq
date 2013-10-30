package org.elasticmq.rest.sqs.directives

import spray.routing._
import spray.http.FormData

trait AnyParamDirectives2 {
  this: Directives =>

  def anyParamsMap(body: Map[String, String] => Route) = {
    parameterMap { queryParameters =>
      entity(as[FormData]) { formData =>
        val allParameters = formData.fields ++ queryParameters
        body(allParameters.toMap)
      }
    }
  }
}
