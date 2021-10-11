package org.elasticmq.persistence.sql

import akka.actor.{Actor, ActorRef}
import akka.util.Timeout
import org.elasticmq.ElasticMQError
import org.elasticmq.actor.queue._
import org.elasticmq.actor.reply._
import org.elasticmq.msg.RestoreMessages
import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.util.Logging
import org.joda.time.DateTime

import scala.collection.mutable
import scala.concurrent.Await

class SqlQueuePersistenceActor(baseQueues: List[CreateQueueMetadata], messagePersistenceConfig: SqlQueuePersistenceConfig) extends Actor with Logging {

  private val queueRepo: QueueRepository = new QueueRepository(messagePersistenceConfig)
  private val repos: mutable.Map[String, MessageRepository] = mutable.HashMap[String, MessageRepository]()

  def receive: Receive = {
    case QueueCreated(queueData) =>
      if (repos.contains(queueData.name)) {
        queueRepo.update(CreateQueueMetadata.from(queueData))
      } else {
        queueRepo.add(CreateQueueMetadata.from(queueData))
        repos.put(queueData.name, new MessageRepository(queueData.name, messagePersistenceConfig))
      }

    case QueueDeleted(queueName) =>
      queueRepo.remove(queueName)
      repos.remove(queueName).foreach(_.drop())

    case QueueMetadataUpdated(queueData) =>
      queueRepo.update(CreateQueueMetadata.from(queueData))

    case AddMessage(queueName, message) =>
      repos.get(queueName).foreach(_.add(message))
      sender() ! OperationSuccessful

    case UpdateMessage(queueName, message) =>
      repos.get(queueName).foreach(_.update(message))
      sender() ! OperationSuccessful

    case RemoveMessage(queueName, messageId) =>
      repos.get(queueName).foreach(_.remove(messageId))
      sender() ! OperationSuccessful

    case Restore(queueManagerActor: ActorRef) =>
      sender() ! createQueues(queueManagerActor)
  }

  private def createQueues(queueManagerActor: ActorRef): Either[List[ElasticMQError], OperationStatus] = {
    implicit val timeout: Timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    val persistedQueues = queueRepo.findAll()
    val persistedQueuesNames = persistedQueues.map(_.name).toSet
    val allQueues = persistedQueues ++ baseQueues.filterNot(queue => persistedQueuesNames.contains(queue.name))

    val errors = allQueues.flatMap { cq =>
      Await.result(queueManagerActor ? org.elasticmq.msg.CreateQueue(cq.toQueueData(new DateTime())), timeout.duration)
        .map(queueActor => restoreMessages(cq.name, queueActor))
        .swap
        .toOption
    }

    if (errors.nonEmpty) Left(errors) else Right(OperationSuccessful)
  }

  private def restoreMessages(queueName: String, queueActor: ActorRef)(implicit timeout: Timeout): Unit = {
    val repository = new MessageRepository(queueName, messagePersistenceConfig)
    repos.put(queueName, repository)
    Await.result(queueActor ? RestoreMessages(repository.findAll()), timeout.duration)
  }
}
