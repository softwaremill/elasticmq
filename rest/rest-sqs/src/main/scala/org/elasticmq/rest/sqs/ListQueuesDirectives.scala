package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ListQueues

trait ListQueuesDirectives { this: ElasticMQDirectives with QueueURLModule =>
  val listQueues = {
    action("ListQueues") {
      rootPath {
        anyParam("QueueNamePrefix"?) { prefixOption =>
          for {
            allQueueNames <- queueManagerActor ? ListQueues()
          } yield  {
            val queueNames = prefixOption match {
              case Some(prefix) => allQueueNames.filter(_.startsWith(prefix))
              case None => allQueueNames
            }

            respondWith {
              <ListQueuesResponse>
                <ListQueuesResult>
                  {queueNames.map(queueName => <QueueUrl>{queueURL(queueName)}</QueueUrl>)}
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
}