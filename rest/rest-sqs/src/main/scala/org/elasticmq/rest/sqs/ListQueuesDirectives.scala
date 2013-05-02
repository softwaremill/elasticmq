package org.elasticmq.rest.sqs

import Constants._

trait ListQueuesDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val listQueues = {
    action("ListQueues") {
      rootPath {
        anyParam("QueueNamePrefix"?) { prefixOption =>
          val allQueues = client.listQueues

          val queues = prefixOption match {
            case Some(prefix) => allQueues.filter(_.name.startsWith(prefix))
            case None => allQueues
          }

          respondWith {
            <ListQueuesResponse>
              <ListQueuesResult>
                {queues.map(q => <QueueUrl>{queueURL(q)}</QueueUrl>)}
              </ListQueuesResult>
              <ResponseMetadata>
                <RequestId>{EmptyRequestId}</RequestId>
              </ResponseMetadata>
            </ListQueuesResponse>
          }
        }
      }
    }
  }
}