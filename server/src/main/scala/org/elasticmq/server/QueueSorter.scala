package org.elasticmq.server

import org.elasticmq.server.config.CreateQueue
import org.elasticmq.util.Logging

import scala.collection.mutable.ListBuffer
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._

object QueueSorter extends Logging {

  /**
    * Reverse topologically sort CreateQueue collection so that referenced queues are created first
    * (this includes dead letter, copy-to and move-to queues).
    *
    * @param cqs
    * @return
    */
  def sortCreateQueues(cqs: List[CreateQueue]): List[CreateQueue] = {
    val nodes = cqs
    val edges = createReferencedQueuesEdges(nodes)
    val sorted = Graph.from(nodes, edges).topologicalSort()

    // There's a cycle somewhere in the graph
    if (sorted.isLeft) {
      throw new IllegalArgumentException(s"Circular queue graph, check ${sorted.left.get.value.name}")
    }

    sorted.right.get.toList.reverse.map { node =>
      node.value
    }
  }

  private def createReferencedQueuesEdges(nodes: List[CreateQueue]): List[DiEdge[CreateQueue]] = {
    val edges = new ListBuffer[DiEdge[CreateQueue]]()

    // create map to look up queues by name
    val queueMap = nodes.map { cq =>
      cq.name -> cq
    }.toMap

    // create directed edges from queue to referenced queues (dead letter queue, copy-to and move-to queues)
    nodes.foreach { cq =>
      val referencedQueues =
        Seq(
          "Dead letter" -> cq.deadLettersQueue.map(_.name),
          "Copy to" -> cq.copyMessagesTo,
          "Move to" -> cq.moveMessagesTo
        ).flatMap {
          case (label, Some(queue)) => Seq(label -> queue)
          case (_, None)            => Nil
        }

      referencedQueues.foreach {
        case (label, queueName) =>
          queueMap.get(queueName) match {
            case Some(queue) => edges += cq ~> queue
            case None        => logger.error("{} queue {} not found", label, queueName)
          }
      }
    }

    edges.toList
  }

}
