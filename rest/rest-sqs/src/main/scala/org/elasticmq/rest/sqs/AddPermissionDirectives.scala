package org.elasticmq.rest.sqs

import Constants._
import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.rest.sqs.Action.AddPermission
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload

trait AddPermissionDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def addPermission(p: RequestPayload, protocol: AWSProtocol) = {
    p.action(AddPermission) {
      protocol match {
        case AWSProtocol.AWSQueryProtocol =>
          respondWith {
            <AddPermissionResponse>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </AddPermissionResponse>
          }
        case _ => complete(200, HttpEntity.Empty)
      }
    }
  }
}
