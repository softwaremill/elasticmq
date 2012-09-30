package org.elasticmq

import org.joda.time.Duration

trait QueueOperations {
  def sendMessage(content: String): Message
  def sendMessage(messageBuilder: MessageBuilder): Message

  def receiveMessage(): Option[Message]
  def receiveMessage(visibilityTimeout: VisibilityTimeout): Option[Message]
  def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout): Option[Pair[Message, MessageStatistics]]

  def receiveMessages(maxCount: Int): List[Message]
  def receiveMessages(visibilityTimeout: VisibilityTimeout, maxCount: Int): List[Message]
  def receiveMessagesWithStatistics(visibilityTimeout: VisibilityTimeout, maxCount: Int): List[Pair[Message, MessageStatistics]]

  def lookupMessage(id: MessageId): Option[Message]

  def updateDefaultVisibilityTimeout(defaultVisibilityTimeout: MillisVisibilityTimeout): Queue
  def updateDelay(delay: Duration): Queue

  def fetchStatistics(): QueueStatistics
  def delete()

  /**
   * Retrieves the current state of the queue form the server.
   */
  def fetchQueue(): Queue

  /**
   * Returns an interface to operations on the given message.
   *
   * This method does not query the server and does not verify if the message exists.
   */
  def messageOperations(id: MessageId): MessageOperations
}
