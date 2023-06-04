package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.Action.RemovePermission
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.rest.sqs.model.RequestPayload

trait RemovePermissionDirectives { this: ElasticMQDirectives with QueueURLModule with ResponseMarshaller =>
  def removePermission(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) = {
    p.action(RemovePermission) {
      emptyResponse("RemovePermissionResponse")
    }
  }
}
