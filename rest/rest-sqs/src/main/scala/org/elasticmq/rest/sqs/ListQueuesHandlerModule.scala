package org.elasticmq.rest.sqs

import org.elasticmq.rest.RequestHandlerBuilder._
import org.elasticmq.rest.RestPath._

import org.jboss.netty.handler.codec.http.HttpMethod._

import Constants._
import ActionUtil._

trait ListQueuesHandlerModule { this: ClientModule with QueueURLModule with RequestHandlerLogicModule =>
  val listQueuesLogic = logic((request, parameters) => {
    val prefixOption = parameters.get("QueueNamePrefix")
    val allQueues = client.listQueues

    val queues = prefixOption match {
      case Some(prefix) => allQueues.filter(_.name.startsWith(prefix))
      case None => allQueues
    }

    <ListQueuesResponse>
      <ListQueuesResult>
        {queues.map(q => <QueueUrl>{queueURL(q)}</QueueUrl>)}
      </ListQueuesResult>
      <ResponseMetadata>
        <RequestId>{EmptyRequestId}</RequestId>
      </ResponseMetadata>
    </ListQueuesResponse>
  })

  val ListQueuesAction = createAction("ListQueues")

  val listQueuesGetHandler = (createHandler
            forMethod GET
            forPath (root)
            requiringParameterValues Map(ListQueuesAction)
            running listQueuesLogic)

  val listQueuesPostHandler = (createHandler
            forMethod POST
            forPath (root)
            includingParametersFromBody()
            requiringParameterValues Map(ListQueuesAction)
            running listQueuesLogic)
}