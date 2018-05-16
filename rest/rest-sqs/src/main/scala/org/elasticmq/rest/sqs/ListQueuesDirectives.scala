package org.elasticmq.rest.sqs

import Constants._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.ListQueues
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait ListQueuesDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def listQueues(p: AnyParams) = {
    p.action("ListQueues") {
      rootPath {
        val prefixOption = p.get("QueueNamePrefix")
        for {
          allQueueNames <- queueManagerActor ? ListQueues()
        } yield {
          val queueNames = prefixOption match {
            case Some(prefix) => allQueueNames.filter(_.startsWith(prefix))
            case None         => allQueueNames
          }

          baseQueueURL { baseURL =>
            respondWith {
              <ListQueuesResponse>
                <ListQueuesResult>
                  {queueNames.map(queueName => <QueueUrl>{baseURL + "/" + queueName}</QueueUrl>)}
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
