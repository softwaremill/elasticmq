package org.elasticmq.rest.sqs

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.{Directive1, Route}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Timeout
import org.elasticmq._
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.metrics.QueuesMetrics
import org.elasticmq.rest.sqs.Constants._
import org.elasticmq.rest.sqs.XmlNsVersion.extractXmlNs
import org.elasticmq.rest.sqs.directives.{
  AWSProtocolDirectives,
  AnyParamDirectives,
  ElasticMQDirectives,
  UnmatchedActionRoutes
}
import org.elasticmq.rest.sqs.model.RequestPayload
import org.elasticmq.util.{Logging, NowProvider}

import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.management.ObjectName
import scala.collection.immutable.TreeMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import scala.xml._

/** Entry point for starting an embedded SQS REST server: configure it with the `withX` methods, then call `start()`.
  *
  * Defaults:
  *   - binds to `localhost:9324`,
  *   - returned queue URLs use `http://localhost:9324` as the base address (with the actual port, if a dynamic port is
  *     used),
  *   - strict SQS limits ([[StrictSQSLimits]]),
  *   - region `elasticmq`, account id `000000000000`,
  *   - a new, server-owned actor system,
  *   - no queue event listener, queue auto-creation disabled.
  */
object SQSRestServerBuilder
    extends TheSQSRestServerBuilder(
      providedActorSystem = None,
      providedQueueManagerActor = None,
      interface = "",
      port = 9324,
      serverAddress = NodeAddress(),
      generateServerAddress = true,
      sqsLimits = StrictSQSLimits,
      awsRegion = "elasticmq",
      awsAccountId = "000000000000",
      queueEventListener = None,
      queueEventListenerActor = None,
      autoCreateQueuesConfig = AutoCreateQueuesConfig.disabled
    )

/** An immutable SQS REST server configuration. Obtain one from [[SQSRestServerBuilder]]; every `withX` method returns a
  * new instance.
  */
sealed class TheSQSRestServerBuilder private[sqs] (
    providedActorSystem: Option[ActorSystem],
    providedQueueManagerActor: Option[ActorRef],
    interface: String,
    port: Int,
    serverAddress: NodeAddress,
    generateServerAddress: Boolean,
    sqsLimits: Limits,
    awsRegion: String,
    awsAccountId: String,
    queueEventListener: Option[QueueEventListener],
    queueEventListenerActor: Option[ActorRef],
    autoCreateQueuesConfig: AutoCreateQueuesConfig
) extends Logging {

  private def copy(
      providedActorSystem: Option[ActorSystem] = providedActorSystem,
      providedQueueManagerActor: Option[ActorRef] = providedQueueManagerActor,
      interface: String = interface,
      port: Int = port,
      serverAddress: NodeAddress = serverAddress,
      generateServerAddress: Boolean = generateServerAddress,
      sqsLimits: Limits = sqsLimits,
      awsRegion: String = awsRegion,
      awsAccountId: String = awsAccountId,
      queueEventListener: Option[QueueEventListener] = queueEventListener,
      queueEventListenerActor: Option[ActorRef] = queueEventListenerActor,
      autoCreateQueuesConfig: AutoCreateQueuesConfig = autoCreateQueuesConfig
  ): TheSQSRestServerBuilder =
    new TheSQSRestServerBuilder(
      providedActorSystem,
      providedQueueManagerActor,
      interface,
      port,
      serverAddress,
      generateServerAddress,
      sqsLimits,
      awsRegion,
      awsAccountId,
      queueEventListener,
      queueEventListenerActor,
      autoCreateQueuesConfig
    )

  /** @param actorSystem
    *   Actor system in which the server's actors and HTTP layer run. Its lifecycle stays with the caller: stopping the
    *   server does not terminate it. If none is provided, the server creates its own and terminates it on stop.
    */
  def withActorSystem(actorSystem: ActorSystem): TheSQSRestServerBuilder =
    copy(providedActorSystem = Some(actorSystem))

  /** @param interface
    *   Hostname to which the server will bind.
    */
  def withInterface(interface: String): TheSQSRestServerBuilder = copy(interface = interface)

  /** @param port
    *   Port to which the server will bind.
    */
  def withPort(port: Int): TheSQSRestServerBuilder = copy(port = port)

  /** Will assign port automatically (uses port 0). The port to which the socket binds will be logged on successful
    * startup, and is available from [[SQSRestServer.waitUntilStarted]].
    */
  def withDynamicPort(): TheSQSRestServerBuilder = withPort(0)

  /** @param serverAddress
    *   Address which will be returned as the queue address. Requests to this address should be routed to this server.
    */
  def withServerAddress(serverAddress: NodeAddress): TheSQSRestServerBuilder =
    copy(serverAddress = serverAddress, generateServerAddress = false)

  /** @param sqsLimits
    *   Should "real" SQS limits be used (strict), or should they be relaxed where possible (regarding e.g. message
    *   size).
    */
  def withSQSLimits(sqsLimits: Limits): TheSQSRestServerBuilder = copy(sqsLimits = sqsLimits)

  /** @param region
    *   Region which will be included in ARN resource ids.
    */
  def withAWSRegion(region: String): TheSQSRestServerBuilder = copy(awsRegion = region)

  /** @param accountId
    *   AccountId which will be included in ARN resource ids.
    */
  def withAWSAccountId(accountId: String): TheSQSRestServerBuilder = copy(awsAccountId = accountId)

  /** @param listener
    *   Listener notified of changes in queues and messages. See [[QueueEventListener]] for the threading and blocking
    *   semantics.
    */
  def withQueueEventListener(listener: QueueEventListener): TheSQSRestServerBuilder =
    copy(queueEventListener = Some(listener))

  /** @param autoCreateQueues
    *   Configuration for auto-creating queues on demand when a message is sent to a non-existing queue.
    */
  def withAutoCreateQueues(autoCreateQueues: AutoCreateQueuesConfig): TheSQSRestServerBuilder =
    copy(autoCreateQueuesConfig = autoCreateQueues)

  /** Internal: serve queues of an existing `QueueManagerActor` (e.g. one wired to a persistence actor) instead of
    * creating a new one. Not part of the public API.
    */
  private[elasticmq] def withQueueManagerActor(queueManagerActor: ActorRef): TheSQSRestServerBuilder =
    copy(providedQueueManagerActor = Some(queueManagerActor))

  /** Internal: an actor speaking the `QueueEvent` protocol (e.g. a persistence actor), passed to the queue manager the
    * server creates. Not part of the public API; embedders use [[withQueueEventListener]].
    */
  private[elasticmq] def withQueueEventListenerActor(listenerActor: ActorRef): TheSQSRestServerBuilder =
    copy(queueEventListenerActor = Some(listenerActor))

  def start(): SQSRestServer = {
    val (theActorSystem, stopActorSystem) = getOrCreateActorSystem
    val theQueueManagerActor = getOrCreateQueueManagerActor(theActorSystem)
    val theServerAddress =
      if (generateServerAddress)
        NodeAddress(host = if (interface.isEmpty) "localhost" else interface, port = port)
      else serverAddress
    val theLimits = sqsLimits
    val theAutoCreateQueues = autoCreateQueuesConfig
    val theAwsRegion = awsRegion
    val theAwsAccountId = awsAccountId

    implicit val implicitActorSystem: ActorSystem = theActorSystem
    implicit val implicitMaterializer: Materializer = Materializer(theActorSystem)

    val currentServerAddress =
      new AtomicReference[NodeAddress](theServerAddress)

    val env = new QueueManagerActorModule
      with QueueURLModule
      with ContextPathModule
      with SQSLimitsModule
      with BatchRequestsModule
      with ElasticMQDirectives
      with AnyParamDirectives
      with CreateQueueDirectives
      with DeleteQueueDirectives
      with AWSProtocolDirectives
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
      with PurgeQueueDirectives
      with AddPermissionDirectives
      with RemovePermissionDirectives
      with AttributesModule
      with TagQueueDirectives
      with TagsModule
      with UnmatchedActionRoutes
      with ResponseMarshaller
      with QueueAttributesOps
      with ListDeadLetterSourceQueuesDirectives
      with StartMessageMoveTaskDirectives
      with CancelMessageMoveTaskDirectives
      with ListMessageMoveTasksDirectives
      with AutoCreateQueuesModule {

      def serverAddress = currentServerAddress.get()

      lazy val actorSystem = theActorSystem
      lazy val materializer = implicitMaterializer
      lazy val queueManagerActor = theQueueManagerActor
      lazy val sqsLimits = theLimits
      lazy val timeout = Timeout(21, TimeUnit.SECONDS) // see application.conf
      lazy val contextPath = serverAddress.contextPathStripped

      lazy val awsRegion: String = theAwsRegion
      lazy val awsAccountId: String = theAwsAccountId
      lazy val autoCreateQueues: AutoCreateQueuesConfig = theAutoCreateQueues

    }

    import env._
    def rawRoutes(p: RequestPayload)(implicit marshallerDependencies: MarshallerDependencies) =
      // 1. Sending, receiving, deleting messages
      sendMessage(p) ~
        sendMessageBatch(p) ~
        receiveMessage(p) ~
        deleteMessage(p) ~
        deleteMessageBatch(p) ~
        // 2. Getting, creating queues
        getQueueUrl(p) ~
        createQueue(p) ~
        listQueues(p) ~
        purgeQueue(p) ~
        // 3. Other
        changeMessageVisibility(p) ~
        changeMessageVisibilityBatch(p) ~
        deleteQueue(p) ~
        getQueueAttributes(p) ~
        setQueueAttributes(p) ~
        addPermission(p) ~
        removePermission(p) ~
        tagQueue(p) ~
        untagQueue(p) ~
        listQueueTags(p) ~
        listDeadLetterSourceQueues(p) ~
        startMessageMoveTask(p) ~
        cancelMessageMoveTask(p) ~
        listMessageMoveTasks(p) ~
        // 4. Unmatched action
        unmatchedAction(p)

    val config = new ElasticMQConfig

    val healthCheckRoute = path("health") {
      get {
        complete("OK")
      }
    }

    val sqsRoute =
      extractXmlNs { (_version: XmlNsVersion) =>
        implicit val version: XmlNsVersion = _version
        extractProtocol { (_protocol: AWSProtocol) =>
          implicit val protocol: AWSProtocol = _protocol
          handleServerExceptions(protocol) {
            handleRejectionsWithSQSError(protocol) {
              anyParamsMap(protocol) { p =>
                implicit val marshallerDependencies: MarshallerDependencies = MarshallerDependencies(protocol, version)
                if (config.debug) {
                  logRequestResult("") {
                    rawRoutes(p)
                  }
                } else rawRoutes(p)
              }
            }
          }
        }
      }

    val routes = concat(healthCheckRoute, sqsRoute)

    val appStartFuture = HttpServer.bind(implicitActorSystem, interface, port, routes)

    appStartFuture.foreach { (sb: Http.ServerBinding) =>
      if (generateServerAddress && port != sb.localAddress.getPort) {
        currentServerAddress.set(theServerAddress.copy(port = sb.localAddress.getPort))
      }

      TheSQSRestServerBuilder.this.logger.info(
        "Started SQS rest server, bind address %s:%d, visible server address %s"
          .format(
            interface,
            sb.localAddress.getPort,
            if (env.serverAddress.isWildcard)
              "* (depends on incoming request path) "
            else env.serverAddress.fullAddress
          )
      )
    }

    appStartFuture.failed.foreach {
      case NonFatal(e) =>
        TheSQSRestServerBuilder.this.logger
          .error("Cannot start SQS rest server, bind address %s:%d".format(interface, port), e)
      case _ =>
    }

    val queuesMetricsBean = new QueuesMetrics(theQueueManagerActor)
    val platformMBeanServer = ManagementFactory.getPlatformMBeanServer
    val objectName = new ObjectName("org.elasticmq:name=Queues")
    if (!platformMBeanServer.isRegistered(objectName)) {
      Try(platformMBeanServer.registerMBean(queuesMetricsBean, objectName)) match {
        case Success(_) =>
          logger.info("Metrics MBean {} successfully registered", objectName)
        case Failure(exception) =>
          logger.warn(
            "Failed to register metrics MBean. It may happen when multiple instances of the server are started in parallel",
            exception
          )
      }
    }

    new SQSRestServer(
      appStartFuture,
      () => appStartFuture.flatMap(_.terminate(1.minute)).flatMap(_ => stopActorSystem())
    )
  }

  private def getOrCreateActorSystem: (ActorSystem, () => Future[Any]) = {
    providedActorSystem
      .map((_, () => Future.successful(())))
      .getOrElse {
        val actorSystem = ActorSystem("elasticmq")
        (actorSystem, () => actorSystem.terminate())
      }
  }

  private def getOrCreateQueueManagerActor(actorSystem: ActorSystem): ActorRef = {
    require(
      queueEventListener.isEmpty || queueEventListenerActor.isEmpty,
      "Only one queue event listener can be configured"
    )
    providedQueueManagerActor match {
      case Some(queueManagerActor) =>
        require(
          queueEventListener.isEmpty && queueEventListenerActor.isEmpty,
          "A queue event listener cannot be combined with a provided queue manager actor"
        )
        queueManagerActor
      case None =>
        val listenerActor = queueEventListenerActor.orElse(
          queueEventListener.map(listener => actorSystem.actorOf(Props(new QueueEventListenerActor(listener))))
        )
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), sqsLimits, listenerActor)))
    }
  }
}

private object HttpServer {
  def bind(actorSystem: ActorSystem, interface: String, port: Int, routes: Route): Future[Http.ServerBinding] = {
    implicit val system: ActorSystem = actorSystem
    Http().newServerAt(interface, port).bindFlow(routes)
  }
}

/** A running (or starting) SQS REST server, returned by [[TheSQSRestServerBuilder.start]]. */
final class SQSRestServer private[sqs] (
    startFuture: Future[Http.ServerBinding],
    stop: () => Future[Any]
) {

  /** Blocks (up to one minute) until the server is bound, and returns the address it is bound to. Useful with
    * [[TheSQSRestServerBuilder.withDynamicPort]] to find out the assigned port.
    */
  def waitUntilStarted(): SQSRestServerBinding =
    new SQSRestServerBinding(Await.result(startFuture, 1.minute).localAddress)

  /** Stops the server (and the actor system, if the server created it), and returns a future completed once done. */
  def stopAndGetFuture(): Future[Unit] = stop().map(_ => ())(ExecutionContext.parasitic)

  /** Stops the server (and the actor system, if the server created it), blocking up to one minute. */
  def stopAndWait(): Unit = Await.result(stopAndGetFuture(), 1.minute)
}

/** @param localAddress
  *   The address the server's socket is bound to.
  */
final class SQSRestServerBinding private[sqs] (val localAddress: InetSocketAddress) {
  override def toString: String = s"SQSRestServerBinding($localAddress)"
}

object Constants {
  val EmptyRequestId = "00000000-0000-0000-0000-000000000000"
  val SqsDefaultVersion = "2012-11-05"
  val ReceiptHandleParameter = "ReceiptHandle"
  val VisibilityTimeoutParameter = "VisibilityTimeout"
  val RedrivePolicyParameter = "RedrivePolicy"
  val DelaySecondsAttribute = "DelaySeconds"
  val ReceiveMessageWaitTimeSecondsAttribute = "ReceiveMessageWaitTimeSeconds"
  val QueueArnAttribute = "QueueArn"
  val IdSubParameter = "Id"
  val InvalidParameterValueErrorName = "InvalidParameterValue"
  val MissingParameterName = "MissingParameter"
  val QueueUrlContext = "queue"
  val QueueUrlParameter = "QueueUrl"
  val QueueNameParameter = "QueueName"
  val MessageBodyParameter = "MessageBody"
  val DelaySecondsParameter = "DelaySeconds"
  val MessageGroupIdParameter = "MessageGroupId"
  val MessageDeduplicationIdParameter = "MessageDeduplicationId"
  val AwsTraceHeaderSystemAttribute = "AWSTraceHeader"
  val MaxResultsParameter = "MaxResults"
  val NextTokenParameter = "NextToken"
  val QueueNamePrefixParameter = "QueueNamePrefix"
  val SourceArnParameter = "SourceArn"
  val DestinationArnParameter = "DestinationArn"
  val MaxNumberOfMessagesPerSecondParameter = "MaxNumberOfMessagesPerSecond"
  val TaskHandleParameter = "TaskHandle"
}

object ParametersUtil {

  implicit class ParametersParser(parameters: Map[String, String]) {
    def parseOptionalLong(name: String) = {
      val param = parameters.get(name)
      try {
        param.map(_.toLong)
      } catch {
        case _: NumberFormatException =>
          throw SQSException.invalidParameterValue
      }
    }
  }

}

object MD5Util {
  def md5Digest(s: String) = {
    val md5 = MessageDigest.getInstance("MD5")
    md5.reset()
    md5.update(s.getBytes("UTF-8"))
    md5
      .digest()
      .map(0xff & _)
      .map {
        "%02x".format(_)
      }
      .foldLeft("") {
        _ + _
      }
  }

  def md5AttributeDigest(attributes: Map[String, MessageAttribute]): String = {
    def addEncodedString(b: ByteArrayOutputStream, s: String) = {
      val str = s.getBytes("UTF-8")
      b.write(
        ByteBuffer.allocate(4).putInt(str.length).array
      ) // Sadly, we'll need ByteBuffer here to get a properly encoded 4-byte int (alternatively, we could encode by hand)
      b.write(str)
    }

    def addEncodedByteArray(b: ByteArrayOutputStream, a: Array[Byte]) = {
      b.write(ByteBuffer.allocate(4).putInt(a.length).array)
      b.write(a)
    }

    val byteStream = new ByteArrayOutputStream

    TreeMap(attributes.toSeq*).foreach { case (k, v) =>
      // TreeMap is for sorting, a requirement of algorithm
      addEncodedString(byteStream, k)
      addEncodedString(byteStream, v.getDataType())

      v match {
        case s: StringMessageAttribute =>
          byteStream.write(1)
          addEncodedString(byteStream, s.stringValue)
        case n: NumberMessageAttribute =>
          byteStream.write(1)
          addEncodedString(byteStream, n.stringValue.toString)
        case b: BinaryMessageAttribute =>
          byteStream.write(2)
          addEncodedByteArray(byteStream, b.binaryValue.toArray)
      }
    }

    val md5 = MessageDigest.getInstance("MD5")
    md5.reset()
    md5.update(byteStream.toByteArray)
    md5
      .digest()
      .map(0xff & _)
      .map {
        "%02x".format(_)
      }
      .foldLeft("") {
        _ + _
      }
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
  def awsAccountId: String

  import org.apache.pekko.http.scaladsl.server.Directives._

  def baseQueueURL: Directive1[String] = {
    val postfix = if (awsAccountId.nonEmpty) awsAccountId else QueueUrlContext

    val baseAddress = if (serverAddress.isWildcard) {
      extractRequest.map { req =>
        val incomingAddress =
          req.uri.copy(rawQueryString = None, fragment = None).toString

        val incomingAddressNoSlash = if (incomingAddress.endsWith("/")) {
          incomingAddress.substring(0, incomingAddress.length - 1)
        } else incomingAddress

        if (incomingAddressNoSlash.endsWith(postfix)) {
          incomingAddressNoSlash.substring(0, incomingAddressNoSlash.length - postfix.length - 1)
        } else incomingAddressNoSlash
      }
    } else {
      provide(serverAddress.fullAddress)
    }

    baseAddress.map(_ + "/" + postfix)
  }

  def queueURL(queueName: String): Directive1[String] = {
    baseQueueURL.map(base => base + "/" + queueName)
  }
}

trait ContextPathModule {
  def contextPath: String
}

trait SQSLimitsModule {
  def sqsLimits: Limits
}

class ElasticMQConfig {
  private lazy val rootConfig = ConfigFactory.load()
  private lazy val elasticMQConfig = rootConfig.getConfig("elasticmq")

  lazy val debug = elasticMQConfig.getBoolean("debug")
}
