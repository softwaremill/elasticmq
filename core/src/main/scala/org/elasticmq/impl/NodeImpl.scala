package org.elasticmq.impl

import org.elasticmq.{Client, Node}
import com.typesafe.scalalogging.slf4j.Logging

class NodeImpl(client: Client, storageShutdown: () => Unit) extends Node with Logging {
  def nativeClient = client

  def shutdown() {
    storageShutdown()

    logger.info("Node shut down")
  }
}