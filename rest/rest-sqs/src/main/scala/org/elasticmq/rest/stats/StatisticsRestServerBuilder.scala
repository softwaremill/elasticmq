package org.elasticmq.rest.stats

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.elasticmq._
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.rest.sqs.QueueAttributesOps
import org.elasticmq.rest.sqs.directives.ElasticMQDirectives
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

/** By default:
  * <li>
  * <ul>for `socketAddress`: when started, the server will bind to `localhost:9325`</ul>
  * <ul>for `serverAddress`: returned queue addresses will use `http://localhost:9325` as the base address.</ul>
  * <ul>for `sqsLimits`: relaxed
  * </li>
  */
object StatisticsRestServerBuilder
  extends TheStatisticsRestServerBuilder(
    None,
    None,
    "",
    9325,
    NodeAddress().copy(port = 9325),
    true,
    "elasticmq",
    "000000000000"
  )

case class TheStatisticsRestServerBuilder(
                                           providedActorSystem: Option[ActorSystem],
                                           providedQueueManagerActor: Option[ActorRef],
                                           interface: String,
                                           port: Int,
                                           serverAddress: NodeAddress,
                                           generateServerAddress: Boolean,
                                           _awsRegion: String,
                                           _awsAccountId: String
                                         ) extends Logging {

  /** @param _actorSystem Optional actor system. If one is provided, it will be used to create ElasticMQ and Spray
    *                     actors, but its lifecycle (shutdown) will be not managed by the server. If one is not
    *                     provided, an actor system will be created, and its lifecycle will be bound to the server's
    *                     lifecycle.
    */
  def withActorSystem(_actorSystem: ActorSystem) =
    this.copy(providedActorSystem = Some(_actorSystem))

  /** @param _queueManagerActor Optional "main" ElasticMQ actor.
    */
  def withQueueManagerActor(_queueManagerActor: ActorRef) =
    this.copy(providedQueueManagerActor = Some(_queueManagerActor))

  /** @param _interface Hostname to which the server will bind.
    */
  def withInterface(_interface: String) = this.copy(interface = _interface)

  /** @param _port Port to which the server will bind.
    */
  def withPort(_port: Int) = this.copy(port = _port)

  /** Will assign port automatically (uses port 0). The port to which the socket binds will be logged on successful startup.
    */
  def withDynamicPort() = withPort(0)

  /** @param _serverAddress Address which will be returned as the queue address. Requests to this address
    *                       should be routed to this server.
    */
  def withServerAddress(_serverAddress: NodeAddress) =
    this.copy(serverAddress = _serverAddress, generateServerAddress = false)

  /** @param region Region which will be included in ARM resource ids.
    */
  def withAWSRegion(region: String) =
    this.copy(_awsRegion = region)

  /** @param accountId AccountId which will be included in ARM resource ids.
    */
  def withAWSAccountId(accountId: String) =
    this.copy(_awsAccountId = accountId)

  def start(): StatisticsRestServer = {

    implicit val nowProvider = new NowProvider()

    val (theActorSystem, stopActorSystem) = getOrCreateActorSystem
    val theQueueManagerActor = getOrCreateQueueManagerActor(theActorSystem)
    val theServerAddress =
      if (generateServerAddress)
        NodeAddress(host = if (interface.isEmpty) "localhost" else interface, port = port)
      else serverAddress

    implicit val implicitActorSystem = theActorSystem
    implicit val implicitMaterializer = ActorMaterializer()

    val currentServerAddress =
      new AtomicReference[NodeAddress](theServerAddress)

    val env = new StatisticsDirectives
      with QueueAttributesOps
      with ElasticMQDirectives {

      def serverAddress = currentServerAddress.get()

      lazy val actorSystem = theActorSystem
      lazy val materializer = implicitMaterializer
      lazy val queueManagerActor = theQueueManagerActor
      lazy val timeout = Timeout(21, TimeUnit.SECONDS) // see application.conf

      lazy val awsRegion: String = _awsRegion
      lazy val awsAccountId: String = _awsAccountId

    }

    import env._

    implicit val bindingTimeout = Timeout(10, TimeUnit.SECONDS)

    val routes =
      handleServerExceptions {
        statistics
      }

    val appStartFuture = Http().newServerAt(interface, port).bindFlow(routes)

    appStartFuture.foreach { sb: Http.ServerBinding =>
      if (generateServerAddress && port != sb.localAddress.getPort) {
        currentServerAddress.set(theServerAddress.copy(port = sb.localAddress.getPort))
      }

      TheStatisticsRestServerBuilder.this.logger.info(
        "Started statistics rest server, bind address %s:%d, visible server address %s"
          .format(
            interface,
            sb.localAddress.getPort,
            if (env.serverAddress.isWildcard)
              "* (depends on incoming request path) "
            else env.serverAddress.fullAddress
          )
      )
    }

    appStartFuture.failed.foreach { case NonFatal(e) =>
      TheStatisticsRestServerBuilder.this.logger
        .error("Cannot start statistics rest server, bind address %s:%d".format(interface, port), e)
    }

    StatisticsRestServer(
      appStartFuture,
      () => appStartFuture.flatMap(_.unbind()).flatMap(_ => stopActorSystem())
    )
  }

  private def getOrCreateActorSystem: (ActorSystem, () => Future[Any]) = {
    providedActorSystem
      .map((_, () => Future.successful(())))
      .getOrElse {
        val actorSystem = ActorSystem("elasticmq")
        (actorSystem, actorSystem.terminate _)
      }
  }

  private def getOrCreateQueueManagerActor(actorSystem: ActorSystem)(implicit nowProvider: NowProvider) = {

    providedQueueManagerActor.getOrElse(actorSystem.actorOf(Props(new QueueManagerActor(nowProvider, StrictSQSLimits))))
  }
}

case class StatisticsRestServer(startFuture: Future[Http.ServerBinding], stopAndGetFuture: () => Future[Any]) {
  def waitUntilStarted() = {
    Await.result(startFuture, 1.minute)
  }

  def stopAndWait() = {
    val stopFuture = stopAndGetFuture()
    Await.result(stopFuture, 1.minute)
  }
}

