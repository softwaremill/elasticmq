package org.elasticmq.impl

import org.elasticmq.Node
import org.elasticmq.storage.Storage

class NodeImpl(storage: Storage, storageShutdown: () => Unit) extends Node {
  def nativeClient = new NativeClientImpl(storage)

  def shutdown() {
    storageShutdown();
  }
}