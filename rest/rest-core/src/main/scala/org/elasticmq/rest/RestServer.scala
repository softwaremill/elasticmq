package org.elasticmq.rest

import java.util.concurrent.Executors
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.logging.{InternalLoggerFactory, Log4JLoggerFactory}
import org.apache.log4j.{Logger, BasicConfigurator}
import org.jboss.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.handler.execution.{ExecutionHandler, OrderedMemoryAwareThreadPoolExecutor}

class RestServer(doStop: () => Unit) {
  def stop() {
    doStop()
  }
}

class RestPipelineFactory(handlers: List[CheckingRequestHandlerWrapper],
                          allChannels: ChannelGroup) extends ChannelPipelineFactory {
  def getPipeline = {
    val pipeline: ChannelPipeline = Channels.pipeline()

    pipeline.addLast("addChannelToGroup", new AddChannelToGroupHandler(allChannels))

    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(65536))
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("chunkedWriter", new ChunkedWriteHandler)

    // The REST handler can take some time (invoking business logic), so running it in a separate thread pool
    pipeline.addLast("executionHandler", new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576)))
    pipeline.addLast("rest", new RestHandler(handlers))

    pipeline
  }
}

object RestServer {
  def start(handlers: List[CheckingRequestHandlerWrapper], port: Int): RestServer = {
    val factory: ChannelFactory =
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool());

    val bootstrap = new ServerBootstrap(factory);

    val allChannels = new DefaultChannelGroup

    bootstrap.setPipelineFactory(new RestPipelineFactory(handlers, allChannels));

    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);

    val serverChannel = bootstrap.bind(new InetSocketAddress(port));
    allChannels.add(serverChannel)

    new RestServer(() => {
      allChannels.close().awaitUninterruptibly()
      bootstrap.releaseExternalResources()
    })
  }
}

object Testing {
  def createTestHandler = {
    import RequestHandlerBuilder._
    import RestPath._

    (createHandler
            forMethod HttpMethod.GET
            forPath (root / "test" / "me")
            requiringParameters List("param1")
            running (new RequestHandlerLogic() {
      def handle(request: HttpRequest, parameters: Map[String, String]) = StringResponse("OK!")
    }))
  }

  def main(args: Array[String]) {
    BasicConfigurator.configure();
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory())

    val server = RestServer.start(List(createTestHandler), 8888)
    println("Started")
    val logger: Logger = Logger.getLogger(classOf[RestServer].getName)
    logger.info("info")
    logger.debug("debug")

    readLine()
    server.stop()
    println("Stopped")
  }
}