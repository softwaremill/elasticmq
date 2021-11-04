package org.elasticmq.persistence

object TopologicalSorter {
  def apply[T](nodes: Set[Node[T]], edges: Map[Node[T], Set[Node[T]]]): List[T] = {
    val state = State(nodes, List.empty, Set.empty[Node[T]])
    nodes.foldLeft(state)((acc, item) => processNode(acc, item, edges)).ordered.map(_.value)
  }

  private def processNode[T](state: State[T], currentNode: Node[T], edges: Map[Node[T], Set[Node[T]]]): State[T] = {
    if (state.stack.contains(currentNode)) {
      throw new IllegalArgumentException(s"Circular queue graph, check ${state.stack.map(_.value)}")
    }
    if (state.notVisited.contains(currentNode)) {
      val currentNodeEdges = edges.getOrElse(currentNode, Set.empty)
      if (currentNodeEdges.nonEmpty) {
        val markCurrentVisited =
          state.copy(notVisited = state.notVisited - currentNode, stack = state.stack + currentNode)
        val stateAfterChildren =
          currentNodeEdges.foldLeft(markCurrentVisited)((acc, item) => processNode(acc, item, edges))
        stateAfterChildren.copy(
          ordered = stateAfterChildren.ordered :+ currentNode,
          stack = stateAfterChildren.stack - currentNode
        )
      } else {
        state.copy(notVisited = state.notVisited - currentNode, ordered = state.ordered :+ currentNode)
      }
    } else {
      state
    }
  }

  case class State[T](
      notVisited: Set[Node[T]],
      ordered: List[Node[T]],
      stack: Set[Node[T]]
  )

  case class Node[T](value: T)
}
