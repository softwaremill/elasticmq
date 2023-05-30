package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.Action.AddPermission
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload

trait AddPermissionDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>
  def addPermission(p: RequestPayload)(implicit protocol: AWSProtocol) = {
    p.action(AddPermission) {
      emptyResponse("AddPermissionResponse")
    }
  }
}
