package org.elasticmq.rest.sqs

import org.elasticmq.rest.RestPath._
import org.elasticmq.rest.RestServer

import xml.{Null, UnprefixedAttribute}
import java.security.MessageDigest
import org.elasticmq.{NodeAddress, Queue, Client}
import java.net.{InetSocketAddress, SocketAddress}
import com.weiglewilczek.slf4s.Logging

object SQSRestServerFactory extends Logging {
  /**
   * Starts the SQS server on `localhost:9324`. The returned queue addresses will use `http://localhost:9324` as
   * the base address.
   */
  def start(client: Client): RestServer = {
    start(client, 9324, NodeAddress())
  }

  /**
   * @param port Port on which the server will listen.
   * @param serverAddress Address which will be returned as the queue address. Requests to this address
   * should be routed to this server.
   */
  def start(client: Client, port: Int, serverAddress: NodeAddress): RestServer = {
    start(client, new InetSocketAddress(port), serverAddress)
  }

  /**
   * @param socketAddress Address on which the server will listen.
   * @param serverAddress Address which will be returned as the queue address. Requests to this address
   * should be routed to this server.
   */
  def start(client: Client, socketAddress: SocketAddress, serverAddress: NodeAddress): RestServer = {
    val theClient = client
    val theServerAddress = serverAddress

    val env = new ClientModule
      with QueueURLModule
      with RequestHandlerLogicModule
      with CreateQueueHandlerModule
      with DeleteQueueHandlerModule
      with QueueAttributesHandlersModule
      with ListQueuesHandlerModule
      with SendMessageHandlerModule
      with ReceiveMessageHandlerModule
      with DeleteMessageHandlerModule
      with ChangeMessageVisibilityHandlerModule
      with GetQueueUrlHandlerModule
      with AttributesModule {
      val client = theClient
      val serverAddress = theServerAddress
    }

    import env._
    val server = RestServer.start(
        // 1. Sending, receiving, deleting messages
        sendMessageGetHandler :: sendMessagePostHandler ::
        receiveMessageGetHandler :: receiveMessagePostHandler ::
        deleteMessageGetHandler :: deleteMessagePostHandler ::
        // 2. Getting, creating queues
        getQueueUrlGetHandler :: getQueueUrlPostHandler ::
        createQueueGetHandler :: createQueuePostHandler ::
        listQueuesGetHandler :: listQueuesPostHandler ::
        // 3. Other
        changeMessageVisibilityGetHandler :: changeMessageVisibilityPostHandler ::
        deleteQueueGetHandler :: deleteQueuePostHandler ::
        getQueueAttributesGetHandler :: getQueueAttributesPostHandler ::
        setQueueAttributesGetHandler :: setQueueAttributesPostHandler ::
        Nil, socketAddress)

    logger.info("Started SQS rest server, bind address %s, visible server address %s"
      .format(socketAddress, theServerAddress.fullAddress))

    server
  }
}

object Constants {
  val EmptyRequestId = "00000000-0000-0000-0000-000000000000"
  val SqsNamespace = new UnprefixedAttribute("xmlns", "http://queue.amazonaws.com/doc/2009-02-01/", Null)
  val QueueUrlPath = "queue"
  val QueuePath = root / QueueUrlPath / %("QueueName")
  val QueueNameParameter = "QueueName"
  val ReceiptHandlerParameter = "ReceiptHandle"
  val VisibilityTimeoutParameter = "VisibilityTimeout"
  val DelayParameter = "DelaySeconds"
}

object ActionUtil {
  def createAction(action: String) = "Action" -> action
}

object ParametersParserUtil {
  class ParametersParser(parameters: Map[String, String]) {
    def parseOptionalLong(name: String) = {
      val param = parameters.get(name)
      try {
        param.map(_.toLong)
      } catch {
        case e: NumberFormatException => throw SQSException.invalidParameterValue
      }
    }
  }

  implicit def mapToParametersParser(parameters: Map[String, String]): ParametersParser = new ParametersParser(parameters)
}

object MD5Util {
  def md5Digest(s: String) = {
    val md5 = MessageDigest.getInstance("MD5")
    md5.reset()
    md5.update(s.getBytes)
    md5.digest().map(0xFF & _).map { "%02x".format(_) }.foldLeft(""){_ + _}
  }
}

trait ClientModule {
  def client: Client
}

trait QueueURLModule {
  def serverAddress: NodeAddress

  def queueURL(queue: Queue) = serverAddress.fullAddress+"/"+Constants.QueueUrlPath+"/"+queue.name
}



