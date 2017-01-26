package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait AddPermissionDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def addPermission(p: AnyParams) = {
    p.action("AddPermission") {
      respondWith {
        <AddPermissionResponse>
          <ResponseMetadata>
            <RequestId>{EmptyRequestId}</RequestId>
          </ResponseMetadata>
        </AddPermissionResponse>
      }
    }
  }
}