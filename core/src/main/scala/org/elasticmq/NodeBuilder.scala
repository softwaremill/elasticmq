package org.elasticmq

import impl.NodeImpl
import storage.squeryl.SquerylStorage

object NodeBuilder {
  def createNode: Node = {
    SquerylStorage.initialize("elasticmq")
    val storage = new SquerylStorage()
    new NodeImpl(storage, () => SquerylStorage.shutdown())
  }
}