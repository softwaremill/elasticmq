package org.elasticmq

trait QueueOperations {
  def sendMessage(content: String): Message = sendMessage(MessageBuilder(content))
  def sendMessage(messageBuilder: MessageBuilder): Message

  def receiveMessage(): Option[Message] = receiveMessage(DefaultVisibilityTimeout)
  def receiveMessage(visibilityTimeout: VisibilityTimeout): Option[Message]
  def receiveMessageWithStatistics(visibilityTimeout: VisibilityTimeout): Option[Pair[Message, MessageStatistics]]

  def lookupMessage(id: MessageId): Option[Message]

  def fetchStatistics(): QueueStatistics
  def delete()

  /** Retrieves the current state of the queue form the server.
   */
  def fetchQueue(): Queue

  /** Returns an interface to operations on the given message.
    *
    * This method does not query the server and does not verify if the message exists.
    */
  def messageOperations(id: MessageId): MessageOperations
}
