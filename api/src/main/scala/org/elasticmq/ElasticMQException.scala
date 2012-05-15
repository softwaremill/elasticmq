package org.elasticmq

abstract class ElasticMQException(val code: String, message: String, cause: Throwable)
  extends RuntimeException(message, cause)

class QueueDoesNotExistException(queueName: String)
  extends ElasticMQException("QueueDoesNotExist", "Queue does not exist: "+queueName, null)

class QueueAlreadyExistsException(queueName: String)
  extends ElasticMQException("QueueAlreadyExists", "Queue alread exists: "+queueName, null)

class MessageDoesNotExistException(queueName: String, messageId: MessageId)
  extends ElasticMQException("MessageDoesNotExist", "Message does not exist: "+messageId+" in queue: "+queueName, null)

class NodeIsNotMasterException(masterAddress: Option[NodeAddress])
  extends ElasticMQException("NodeIsNotMaster", "Commands can be only executed on master server: " +
    masterAddress.map(_.hostAndPort).getOrElse("unknown"), null)

class NodeIsNotActiveException(minimumNumberOfNodes: Int, currentNumberOfNodes: Int)
  extends ElasticMQException("NodeIsNotActive",
    "Node is not active. Currently %d nodes are active out of the minimum %d nodes required."
      .format(minimumNumberOfNodes, currentNumberOfNodes), null)