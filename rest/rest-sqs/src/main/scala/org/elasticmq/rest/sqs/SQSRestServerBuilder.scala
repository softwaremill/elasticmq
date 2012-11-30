package org.elasticmq.rest.sqs

import org.elasticmq.rest.RestPath._
import org.elasticmq.rest.RestServer

import xml._
import java.security.MessageDigest
import org.elasticmq.{NodeAddress, Queue, Client}
import java.net.{InetSocketAddress, SocketAddress}
import com.weiglewilczek.slf4s.Logging
import xml.EntityRef
import org.elasticmq.NodeAddress
import collection.mutable.ArrayBuffer

/**
 * @param socketAddress Address on which the server will listen.
 * @param serverAddress Address which will be returned as the queue address. Requests to this address
 * should be routed to this server.
 */
class SQSRestServerBuilder(client: Client,
                           socketAddress: SocketAddress,
                           serverAddress: NodeAddress,
                           sqsLimits: SQSLimits.Value) extends Logging {
  /**
   * @param port Port on which the server will listen.
   * @param serverAddress Address which will be returned as the queue address. Requests to this address
   * should be routed to this server.
   */
  def this(client: Client, port: Int, serverAddress: NodeAddress) = {
    this(client, new InetSocketAddress(port), serverAddress, SQSLimits.Strict)
  }

  /**
   * By default:
   * <li>
   *  <ul>for `socketAddress`: when started, the server will bind to `localhost:9324`</ul>
   *  <ul>for `serverAddress`: returned queue addresses will use `http://localhost:9324` as the base address.</ul>
   *  <ul>for `sqsLimits`: relaxed
   * </li>
   */
  def this(client: Client) = {
    this(client, 9324, NodeAddress())
  }

  def withPort(port: Int) = {
    new SQSRestServerBuilder(client, new InetSocketAddress(port), serverAddress, sqsLimits)
  }

  def withServerAddress(newServerAddress: NodeAddress) = {
    new SQSRestServerBuilder(client, socketAddress, newServerAddress, sqsLimits)
  }

  def withSocketAddress(newSocketAddress: SocketAddress) = {
    new SQSRestServerBuilder(client, newSocketAddress, serverAddress, sqsLimits)
  }

  def withSQSLimits(newSqsLimits: SQSLimits.Value) = {
    new SQSRestServerBuilder(client, socketAddress, serverAddress, newSqsLimits)
  }

  def start(): RestServer = {
    val theClient = client
    val theServerAddress = serverAddress
    val theLimits = sqsLimits

    val env = new ClientModule
      with QueueURLModule
      with SQSLimitsModule
      with BatchRequestsModule
      with RequestHandlerLogicModule
      with CreateQueueHandlerModule
      with DeleteQueueHandlerModule
      with QueueAttributesHandlersModule
      with ListQueuesHandlerModule
      with SendMessageHandlerModule
      with SendMessageBatchHandlerModule
      with ReceiveMessageHandlerModule
      with DeleteMessageHandlerModule
      with DeleteMessageBatchHandlerModule
      with ChangeMessageVisibilityHandlerModule
      with ChangeMessageVisibilityBatchHandlerModule
      with GetQueueUrlHandlerModule
      with AttributesModule {
      val client = theClient
      val serverAddress = theServerAddress
      val sqsLimits = theLimits
    }

    import env._
    val server = RestServer.start(
        // 1. Sending, receiving, deleting messages
        sendMessageGetHandler :: sendMessagePostHandler ::
        sendMessageBatchGetHandler :: sendMessageBatchPostHandler ::
        receiveMessageGetHandler :: receiveMessagePostHandler ::
        deleteMessageGetHandler :: deleteMessagePostHandler ::
        deleteMessageBatchGetHandler :: deleteMessageBatchPostHandler ::
        // 2. Getting, creating queues
        getQueueUrlGetHandler :: getQueueUrlPostHandler ::
        createQueueGetHandler :: createQueuePostHandler ::
        listQueuesGetHandler :: listQueuesPostHandler ::
        // 3. Other
        changeMessageVisibilityGetHandler :: changeMessageVisibilityPostHandler ::
        changeMessageVisibilityBatchGetHandler :: changeMessageVisibilityBatchPostHandler ::
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
  val ReceiptHandleParameter = "ReceiptHandle"
  val VisibilityTimeoutParameter = "VisibilityTimeout"
  val DelayParameter = "DelaySeconds"
  val IdSubParameter = "Id"
}

object ActionUtil {
  def createAction(action: String) = "Action" -> action
}

object ParametersUtil {
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

object XmlUtil {
  private val CR = EntityRef("#13")

  def convertTexWithCRToNodeSeq(text: String): NodeSeq = {
    val parts = text.split("\r")
    val partsCount = parts.length
    if (partsCount == 1) {
      Text(text)
    } else {
      val combined = new ArrayBuffer[scala.xml.Node]()
      for (i <- 0 until partsCount) {
        combined += Text(parts(i))
        if (i != partsCount - 1) {
          combined += CR
        }
      }

      combined
    }
  }
}

trait ClientModule {
  def client: Client
}

trait QueueURLModule {
  def serverAddress: NodeAddress

  def queueURL(queue: Queue) = serverAddress.fullAddress+"/"+Constants.QueueUrlPath+"/"+queue.name
}

object SQSLimits extends Enumeration {
  val Strict = Value
  val Relaxed = Value
}

trait SQSLimitsModule {
  def sqsLimits: SQSLimits.Value
  def ifStrictLimits(condition: => Boolean)(exception: String) {
    if (sqsLimits == SQSLimits.Strict && condition) {
      throw new SQSException(exception)
    }
  }
}


