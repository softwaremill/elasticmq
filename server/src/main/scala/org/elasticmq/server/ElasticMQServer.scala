package org.elasticmq.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.CreateQueue
import org.elasticmq.rest.sqs.{CreateQueueDirectives, SQSRestServer, TheSQSRestServerBuilder}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueManagerActor = createBase()
    val restServerOpt = optionallyStartRestSqs(queueManagerActor)

    createQueues(queueManagerActor)

    () => {
      restServerOpt.map(_.stopAndGetFuture())
      Await.result(actorSystem.terminate(), Inf)
    }
  }

  private def createBase(): ActorRef = {
    config.storage match {
      case config.InMemoryStorage =>
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider())))
    }
  }

  private def optionallyStartRestSqs(queueManagerActor: ActorRef): Option[SQSRestServer] = {
    if (config.restSqs.enabled) {

      val server = TheSQSRestServerBuilder(Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
        config.generateNodeAddress,
        config.restSqs.sqsLimits).start()

      server.waitUntilStarted()

      Some(server)
    } else {
      None
    }
  }

  private def createQueues(queueManagerActor: ActorRef): Unit = {
    implicit val timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    sortCreateQueues(config.createQueues).map { cq =>
      val f = queueManagerActor ? CreateQueue(configToParams(cq, new DateTime))
      Await.result(f, timeout.duration)
    }
  }

  private def configToParams(cq: config.CreateQueue, now: DateTime): QueueData = {
    QueueData(
      name = cq.name,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        cq.defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)),
      delay = Duration.standardSeconds(cq.delaySeconds.getOrElse(CreateQueueDirectives.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        cq.receiveMessageWaitSeconds.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWait)),
      created = now,
      lastModified = now,
      deadLettersQueue = cq.deadLettersQueue.map(dlq => DeadLettersQueueData(dlq.name, dlq.maxReceiveCount))
    )
  }

  private def sortCreateQueues(cqs: List[config.CreateQueue]): List[config.CreateQueue] = {
    val nodes = cqs
    val edges = createDeadLetterQueueEdges(nodes)
    val sorted = Graph.from(nodes, edges).topologicalSort()

    if (sorted.isLeft) {
      throw new IllegalArgumentException(s"Circular queue graph, check ${sorted.left.get.value.name}")
    }
    sorted.right.get.toList.reverse.map { node => node.value }
  }

  private def createDeadLetterQueueEdges(nodes: List[config.CreateQueue]): List[DiEdge[config.CreateQueue]] = {
    var edges = new ListBuffer[DiEdge[config.CreateQueue]]()

    var queueMap = Map[String, config.CreateQueue]()
    nodes.foreach { cq =>
      queueMap += (cq.name -> cq)
    }

    nodes.foreach { cq =>
      if (cq.deadLettersQueue.nonEmpty) {
        val dlcqName = cq.deadLettersQueue.get.name
        val dlcq = queueMap.get(dlcqName)

        if (dlcq.isEmpty) {
          logger.error("Dead letter queue {} not found", dlcqName)
        }
        else {
          edges += cq ~> dlcq.get
        }
      }
    }

    edges.toList
  }
}
