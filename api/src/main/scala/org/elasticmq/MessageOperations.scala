package org.elasticmq

trait MessageOperations {
  def updateVisibilityTimeout(newVisibilityTimeout: MillisVisibilityTimeout): Message
  def fetchStatistics(): MessageStatistics
  def delete()

  /**
   * Retrieves the current state of the msg form the server.
   */
  def fetchMessage(): Message
}
