package org.elasticmq.rest.sqs

import xml._
import java.security.MessageDigest
import com.typesafe.scalalogging.slf4j.Logging
import collection.mutable.ArrayBuffer
import spray.routing.SimpleRoutingApp
import akka.actor.{ActorRef, ActorSystem}
import spray.can.server.ServerSettings
import akka.util.Timeout
import scala.concurrent.Future
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import spray.can.Http
import akka.io.{Inet, IO}
import org.elasticmq.rest.sqs.Constants._
import scala.xml.EntityRef
import org.elasticmq.QueueData
import org.elasticmq.NodeAddress
import com.typesafe.config.ConfigFactory

/**
 * @param interface Hostname to which the server will bind.
 * @param port Port to which the server will bind.
 * @param serverAddress Address which will be returned as the queue address. Requests to this address
 * should be routed to this server.
 */
class SQSRestServerBuilder(actorSystem: ActorSystem,
                           queueManagerActor: ActorRef,
                           interface: String, port: Int,
                           serverAddress: NodeAddress,
                           sqsLimits: SQSLimits.Value) extends Logging {
  /**
   * @param port Port on which the server will listen.
   * @param serverAddress Address which will be returned as the queue address. Requests to this address
   * should be routed to this server.
   */
  def this(actorSystem: ActorSystem, queueManagerActor: ActorRef, port: Int, serverAddress: NodeAddress) = {
    this(actorSystem, queueManagerActor, "", port, serverAddress, SQSLimits.Strict)
  }

  /**
   * By default:
   * <li>
   *  <ul>for `socketAddress`: when started, the server will bind to `localhost:9324`</ul>
   *  <ul>for `serverAddress`: returned queue addresses will use `http://localhost:9324` as the base address.</ul>
   *  <ul>for `sqsLimits`: relaxed
   * </li>
   */
  def this(actorSystem: ActorSystem, queueManagerActor: ActorRef) = {
    this(actorSystem, queueManagerActor, 9324, NodeAddress())
  }

  def withInterface(_interface: String) = {
    new SQSRestServerBuilder(actorSystem, queueManagerActor, _interface, port, serverAddress, sqsLimits)
  }

  def withPort(_port: Int) = {
    new SQSRestServerBuilder(actorSystem, queueManagerActor, interface, _port, serverAddress, sqsLimits)
  }

  def withServerAddress(_serverAddress: NodeAddress) = {
    new SQSRestServerBuilder(actorSystem, queueManagerActor, interface, port, _serverAddress, sqsLimits)
  }

  def withSQSLimits(_sqsLimits: SQSLimits.Value) = {
    new SQSRestServerBuilder(actorSystem, queueManagerActor, interface, port, serverAddress, _sqsLimits)
  }

  def start(): SQSRestServer = {
    implicit val theActorSystem = actorSystem
    val theQueueManagerActor = queueManagerActor
    val theServerAddress = serverAddress
    val theLimits = sqsLimits

    val env = new QueueManagerActorModule
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

      lazy val actorSystem = theActorSystem
      lazy val queueManagerActor = theQueueManagerActor
      lazy val serverAddress = theServerAddress
      lazy val sqsLimits = theLimits
      lazy val timeout = Timeout(ServerSettings(actorSystem).requestTimeout.toMillis)
    }

    import env._
    val rawRoutes =
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

    val config = new ElasticMQConfig

    val routes = if (config.debug) {
      logRequestResponse("") {
        rawRoutes
      }
    } else rawRoutes

    val serviceActorName = s"elasticmq-rest-sqs-$port"

    val app = new SimpleRoutingApp {}
    val appStartFuture = app.startServer(interface, port, serviceActorName, options = List(Inet.SO.ReuseAddress(on = true))) {
      handleServerExceptions {
        routes
      }
    }

    SQSRestServerBuilder.this.logger.info("Started SQS rest server, bind address %s:%d, visible server address %s"
      .format(interface, port, theServerAddress.fullAddress))

    SQSRestServer(appStartFuture, () => {
      import akka.pattern.ask
      IO(Http).ask(Http.CloseAll)(Timeout(10000L))
    })
  }
}

case class SQSRestServer(startFuture: Future[Any], stopAndGetFuture: () => Future[Any])

object Constants {
  val EmptyRequestId = "00000000-0000-0000-0000-000000000000"
  val SqsDefaultVersion = "2012-11-05"
  val ReceiptHandleParameter = "ReceiptHandle"
  val VisibilityTimeoutParameter = "VisibilityTimeout"
  val DelaySecondsAttribute = "DelaySeconds"
  val ReceiveMessageWaitTimeSecondsAttribute = "ReceiveMessageWaitTimeSeconds"
  val IdSubParameter = "Id"
  val InvalidParameterValueErrorName = "InvalidParameterValue"
}

object ParametersUtil {
  implicit class ParametersParser(parameters: Map[String, String]) {
    def parseOptionalLong(name: String) = {
      val param = parameters.get(name)
      try {
        param.map(_.toLong)
      } catch {
        case e: NumberFormatException => throw SQSException.invalidParameterValue
      }
    }
  }
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

trait QueueManagerActorModule {
  def queueManagerActor: ActorRef
}

trait QueueURLModule {
  def serverAddress: NodeAddress

  def queueURL(queueData: QueueData) = serverAddress.fullAddress + "/queue/" + queueData.name
  def queueURL(queueName: String) = serverAddress.fullAddress + "/queue/" + queueName
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

  def verifyMessageWaitTime(messageWaitTimeOpt: Option[Long]) {
    messageWaitTimeOpt.foreach { messageWaitTime =>
      if (messageWaitTime < 0) {
        throw SQSException.invalidParameterValue
      }

      ifStrictLimits(messageWaitTime > 20 || messageWaitTime < 1) {
        InvalidParameterValueErrorName
      }
    }
  }
}

class ElasticMQConfig {
  private lazy val rootConfig = ConfigFactory.load()
  private lazy val elasticMQConfig = rootConfig.getConfig("elasticmq")

  lazy val debug = elasticMQConfig.getBoolean("debug")
}
