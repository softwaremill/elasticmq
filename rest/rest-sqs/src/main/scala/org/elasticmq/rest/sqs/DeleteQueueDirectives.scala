package org.elasticmq.rest.sqs

import Constants._
import akka.http.scaladsl.model.HttpEntity
import org.elasticmq.actor.reply._

import scala.async.Async._
import org.elasticmq.msg.DeleteQueue
import org.elasticmq.rest.sqs.Action.{DeleteQueue => DeleteQueueAction}
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives

trait DeleteQueueDirectives { this: ElasticMQDirectives with QueueURLModule =>
  def deleteQueue(p: AnyParams, protocol: AWSProtocol) = {
    p.action(DeleteQueueAction) {
      queueActorAndNameFromRequest(p) {
        (queueActor, queueName) => // We need the queue actor just to check that the queue exists
          async {
            await(queueManagerActor ? DeleteQueue(queueName))
            protocol match {
              case AWSProtocol.AWSQueryProtocol =>
                respondWith {
                  <DeleteQueueResponse>
                    <ResponseMetadata>
                      <RequestId>
                        {EmptyRequestId}
                      </RequestId>
                    </ResponseMetadata>
                  </DeleteQueueResponse>
                }
              case _ => complete(status = 200, HttpEntity.Empty)
            }
          }
      }
    }
  }
}
