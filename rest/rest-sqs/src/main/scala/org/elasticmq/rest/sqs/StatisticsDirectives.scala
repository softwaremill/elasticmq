package org.elasticmq.rest.sqs

import akka.http.scaladsl.server.Directives
import org.elasticmq.rest.sqs.directives.RespondDirectives

trait StatisticsDirectives { this: RespondDirectives with Directives =>

  def statistics = {
    pathPrefix("statistics" / "queues") {
      concat (
        pathEndOrSingleSlash {
          respondWith (
            <ABC>
            "OK"
              </ABC>
          )
        },
        path("abc") {
          respondWith (
            <ABC>
            "OK queues"
              </ABC>
          )
        }
      )
    }
  }

}
