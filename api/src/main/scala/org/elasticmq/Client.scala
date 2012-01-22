package org.elasticmq

/** Client to an ElasticMQ [[org.elasticmq.Node]].
  *
  * Were appropriate, methods may throw [[org.elasticmq.QueueDoesNotExistException]] or
  * [[org.elasticmq.MessageDoesNotExistException]] if an operation on a non-existent queue or message is
  * requested. However, under correct API usage these exceptions should not occur.
  */
trait Client {
  def createQueue(name: String): Queue = createQueue(QueueBuilder(name))
  def createQueue(queueBuilder: QueueBuilder): Queue
  
  def lookupQueue(name: String): Option[Queue]

  def lookupOrCreateQueue(name: String): Queue = lookupOrCreateQueue(QueueBuilder(name))
  def lookupOrCreateQueue(queueBuilder: QueueBuilder): Queue
  
  def listQueues: Seq[Queue]

  /** Returns an interface to operations on the given queue.
    *
    * This method does not query the server and does not verify if the queue exists.
    */
  def queueOperations(name: String): QueueOperations
}