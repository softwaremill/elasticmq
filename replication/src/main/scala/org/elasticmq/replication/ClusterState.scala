package org.elasticmq.replication

import java.util.concurrent.atomic.AtomicInteger
import org.elasticmq.NodeIsNotActiveException

class ClusterState(val configuredNumberOfNodes: Int) {
  val minimumNumberOfNodes = configuredNumberOfNodes / 2 + 1

  private val _currentNumberOfNodes = new AtomicInteger(0)
  
  def currentNumberOfNodes = _currentNumberOfNodes.get()
  def currentNumberOfNodes_=(count: Int) { _currentNumberOfNodes.set(count) }

  def assertNodeActive() {
    val current = currentNumberOfNodes
    if (current < minimumNumberOfNodes) {
      throw new NodeIsNotActiveException(minimumNumberOfNodes, current)
    }
  }
}
