package org.elasticmq.server

import org.elasticmq.server.config.CreateQueue
import org.elasticmq.util.Logging

import scala.collection.mutable.ListBuffer
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._

object QueueSorter extends Logging {
  /**
    * Reverse topologically sort CreateQueue collection so that dead letter queues are created first
    * @param cqs
    * @return
    */
  def sortCreateQueues(cqs: List[CreateQueue]): List[CreateQueue] = {
    val nodes = cqs
    val edges = createDeadLetterQueueEdges(nodes)
    val sorted = Graph.from(nodes, edges).topologicalSort()

    // There's a cycle somewhere in the graph
    if (sorted.isLeft) {
      throw new IllegalArgumentException(s"Circular queue graph, check ${sorted.left.get.value.name}")
    }

    sorted.right.get.toList.reverse.map { node => node.value }
  }

  private def createDeadLetterQueueEdges(nodes: List[CreateQueue]): List[DiEdge[CreateQueue]] = {
    var edges = new ListBuffer[DiEdge[CreateQueue]]()

    // create map to look up queues by name
    var queueMap = Map[String, CreateQueue]()
    nodes.foreach { cq =>
      queueMap += (cq.name -> cq)
    }

    // create directed edges from queue -> dead letter queue
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
