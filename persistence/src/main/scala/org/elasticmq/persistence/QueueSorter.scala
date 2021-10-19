package org.elasticmq.persistence

import org.elasticmq.persistence.TopologicalSorter.Node
import org.elasticmq.util.Logging

import scala.collection.mutable

object QueueSorter extends Logging {

  /** Reverse topologically sort CreateQueue collection so that referenced queues are created first (this includes dead
    * letter, copy-to and move-to queues).
    *
    * @param cqs
    * @return
    */
  def sortCreateQueues(cqs: List[CreateQueueMetadata]): List[CreateQueueMetadata] = {
    val nodes = cqs
    val edges = createReferencedQueuesEdges(nodes).map { case (k, v) => Node(k) -> v.map(Node.apply) }
    TopologicalSorter(nodes.map(Node.apply).toSet, edges)
  }

  private def createReferencedQueuesEdges(nodes: List[CreateQueueMetadata]): Map[CreateQueueMetadata, Set[CreateQueueMetadata]] = {
    val edges = new mutable.ListMap[CreateQueueMetadata, Set[CreateQueueMetadata]]

    // create map to look up queues by name
    val queueMap = nodes.map { cq => cq.name -> cq }.toMap

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

      referencedQueues.foreach { case (label, queueName) =>
        queueMap.get(queueName) match {
          case Some(queue) =>
            val edgesForNode = edges.getOrElse(cq, Set.empty)
            edges.put(cq, edgesForNode + queue)
          case None => logger.error("{} queue {} not found", label, queueName)
        }
      }
    }

    edges.toMap
  }
}
