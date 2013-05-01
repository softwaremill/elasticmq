package org.elasticmq.rest.sqs

import org.elasticmq.rest.RestPath._
import org.elasticmq.rest.RestServer

import xml._
import java.security.MessageDigest
import org.elasticmq.{Queue, Client}
import com.typesafe.scalalogging.slf4j.Logging
import xml.EntityRef
import org.elasticmq.NodeAddress
import collection.mutable.ArrayBuffer
import spray.routing.SimpleRoutingApp

/**
 * @param interface Hostname to which the server will bind.
 * @param port Port to which the server will bind.
 * @param serverAddress Address which will be returned as the queue address. Requests to this address
 * should be routed to this server.
 */
class SQSRestServerBuilder(client: Client,
                           interface: String, port: Int,
                           serverAddress: NodeAddress,
                           sqsLimits: SQSLimits.Value) extends Logging {
  /**
   * @param port Port on which the server will listen.
   * @param serverAddress Address which will be returned as the queue address. Requests to this address
   * should be routed to this server.
   */
  def this(client: Client, port: Int, serverAddress: NodeAddress) = {
    this(client, "", port, serverAddress, SQSLimits.Strict)
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

  def withInterface(_interface: String) = {
    new SQSRestServerBuilder(client, _interface, port, serverAddress, sqsLimits)
  }

  def withPort(_port: Int) = {
    new SQSRestServerBuilder(client, interface, _port, serverAddress, sqsLimits)
  }

  def withServerAddress(_ServerAddress: NodeAddress) = {
    new SQSRestServerBuilder(client, interface, port, _ServerAddress, sqsLimits)
  }

  def withSQSLimits(_sqsLimits: SQSLimits.Value) = {
    new SQSRestServerBuilder(client, interface, port, serverAddress, _sqsLimits)
  }

  def start(): RestServer = {
    val theClient = client
    val theServerAddress = serverAddress
    val theLimits = sqsLimits

    val env = new ClientModule
      with QueueURLModule
      with SQSLimitsModule
      with BatchRequestsModule
      with ElasticMQDirectives
      with CreateQueueDirectives
      with DeleteQueueDirectives
      with QueueAttributesDirectives
      with ListQueuesDirectives
      with SendMessageDirectives
      with SendMessageBatchDirectives
      with ReceiveMessageDirectives
      with DeleteMessageDirectives
      with DeleteMessageBatchDirectives
      with ChangeMessageVisibilityDirectives
      with ChangeMessageVisibilityBatchDirectives
      with GetQueueUrlDirectives
      with AttributesModule {
      val client = theClient
      val serverAddress = theServerAddress
      val sqsLimits = theLimits
    }

    import env._
    val routes =
        // 1. Sending, receiving, deleting messages
        sendMessage ~
        sendMessageBatch ~
        receiveMessage ~
        deleteMessage ~
        deleteMessageBatch ~
        // 2. Getting, creating queues
        getQueueUrl ~
        createQueue ~
        listQueues ~
        // 3. Other
        changeMessageVisibility ~
        changeMessageVisibilityBatch ~
        deleteQueue ~
        getQueueAttributes ~
        setQueueAttributes

    val server = new SimpleRoutingApp {
      startServer(interface, port) {
        routes
      }
    }

    logger.info("Started SQS rest server, bind address %s:%d, visible server address %s"
      .format(interface, port, theServerAddress.fullAddress))

    new RestServer(() => {
      server.system.shutdown()
      server.system.awaitTermination()
    })
  }
}

object Constants {
  val EmptyRequestId = "00000000-0000-0000-0000-000000000000"
  val SqsDefaultVersion = "2012-11-05"
  val QueueUrlPath = "queue"
  val QueuePath = root / QueueUrlPath / %("QueueName")
  val QueueNameParameter = "QueueName"
  val ReceiptHandleParameter = "ReceiptHandle"
  val VersionParameter = "Version"
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


