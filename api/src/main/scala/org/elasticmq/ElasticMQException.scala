package org.elasticmq

class ElasticMQException(message: String, cause: Throwable)
  extends RuntimeException(message, cause)

class QueueDoesNotExistException(queueName: String)
  extends ElasticMQException("Queue does not exist: "+queueName, null)

class QueueAlreadyExistsException(queueName: String)
  extends ElasticMQException("Queue alread exists: "+queueName, null)

class MessageDoesNotExistException(queueName: String, messageId: MessageId)
  extends ElasticMQException("Message does not exist: "+messageId+" in queue: "+queueName, null)

class NodeIsNotMasterException(masterAddress: Option[NodeAddress])
  extends ElasticMQException("Commands can be only executed on master server: " +
    masterAddress.map(_.address).getOrElse("unknown"), null)