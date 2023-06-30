package org.elasticmq.rest.stats

import java.util.concurrent.TimeUnit
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.elasticmq.rest.sqs.QueueAttributesOps
import org.elasticmq.rest.sqs.directives.{AWSProtocolDirectives, ElasticMQDirectives}
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

case class TheStatisticsRestServerBuilder(
    providedActorSystem: ActorSystem,
    providedQueueManagerActor: ActorRef,
    interface: String,
    port: Int,
    _awsRegion: String,
    _awsAccountId: String,
    _contextPath: String
) extends Logging {

  /** @param _actorSystem
    *   Optional actor system. If one is provided, it will be used to create ElasticMQ and Spray actors, but its
    *   lifecycle (shutdown) will be not managed by the server. If one is not provided, an actor system will be created,
    *   and its lifecycle will be bound to the server's lifecycle.
    */
  def withActorSystem(_actorSystem: ActorSystem) =
    this.copy(providedActorSystem = _actorSystem)

  /** @param _queueManagerActor
    *   Optional "main" ElasticMQ actor.
    */
  def withQueueManagerActor(_queueManagerActor: ActorRef) =
    this.copy(providedQueueManagerActor = _queueManagerActor)

  /** @param _interface
    *   Hostname to which the server will bind.
    */
  def withInterface(_interface: String) = this.copy(interface = _interface)

  /** @param _port
    *   Port to which the server will bind.
    */
  def withPort(_port: Int) = this.copy(port = _port)

  /** Will assign port automatically (uses port 0). The port to which the socket binds will be logged on successful
    * startup.
    */
  def withDynamicPort() = withPort(0)

  /** @param region
    *   Region which will be included in ARM resource ids.
    */
  def withAWSRegion(region: String) =
    this.copy(_awsRegion = region)

  /** @param accountId
    *   AccountId which will be included in ARM resource ids.
    */
  def withAWSAccountId(accountId: String) =
    this.copy(_awsAccountId = accountId)

  def start(): StatisticsRestServer = {

    implicit val nowProvider = new NowProvider()

    implicit val implicitActorSystem = providedActorSystem
    implicit val implicitMaterializer = ActorMaterializer()

    val env = new StatisticsDirectives with QueueAttributesOps with ElasticMQDirectives with AWSProtocolDirectives {

      lazy val actorSystem = providedActorSystem
      lazy val materializer = implicitMaterializer
      lazy val queueManagerActor = providedQueueManagerActor
      lazy val timeout = Timeout(21, TimeUnit.SECONDS) // see application.conf
      lazy val contextPath = _contextPath

      lazy val awsRegion: String = _awsRegion
      lazy val awsAccountId: String = _awsAccountId

    }

    import env._

    val routes = {
      extractProtocol { protocol =>
        handleServerExceptions(protocol) {
          statistics
        }
      }
    }

    val appStartFuture = Http().newServerAt(interface, port).bindFlow(routes)

    appStartFuture.foreach { sb: Http.ServerBinding =>
      TheStatisticsRestServerBuilder.this.logger.info(
        "Started statistics rest server, bind address %s:%d"
          .format(
            interface,
            sb.localAddress.getPort
          )
      )
    }

    appStartFuture.failed.foreach {
      case NonFatal(e) =>
        TheStatisticsRestServerBuilder.this.logger
          .error("Cannot start statistics rest server, bind address %s:%d".format(interface, port), e)
      case _ =>
    }

    StatisticsRestServer(
      appStartFuture,
      () => appStartFuture.flatMap(_.terminate(1.minute))
    )
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
