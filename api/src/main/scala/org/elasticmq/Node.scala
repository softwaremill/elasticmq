package org.elasticmq

trait Node {
  def nativeClient: Client
  def shutdown()
}