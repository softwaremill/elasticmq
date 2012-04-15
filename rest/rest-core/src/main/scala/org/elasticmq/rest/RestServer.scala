package org.elasticmq.rest

import java.util.concurrent.Executors
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.handler.execution.{ExecutionHandler, OrderedMemoryAwareThreadPoolExecutor}
import com.weiglewilczek.slf4s.Logging
import org.jboss.netty.logging.{Slf4JLoggerFactory, InternalLoggerFactory}
import java.net.SocketAddress

class RestServer(doStop: () => Unit) extends Logging {
  def stop() {
    doStop()

    logger.info("Rest server stopped")
  }
}

class RestPipelineFactory(handlers: List[CheckingRequestHandlerWrapper],
                          allChannels: ChannelGroup) extends ChannelPipelineFactory with Logging {
  val executionHandler = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(16, 1048576, 1048576))

  def getPipeline = {
    val pipeline: ChannelPipeline = Channels.pipeline()

    pipeline.addLast("addChannelToGroup", new AddChannelToGroupHandler(allChannels))

    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(65536))
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("chunkedWriter", new ChunkedWriteHandler)

    // The REST handler can take some time (invoking business logic), so running it in a separate thread pool
    pipeline.addLast("executionHandler", executionHandler)
    pipeline.addLast("rest", new RestHandler(handlers))

    pipeline
  }
}

object RestServer {
  def start(handlers: List[CheckingRequestHandlerWrapper], socketAddress: SocketAddress): RestServer = {
    InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())

    val factory: ChannelFactory =
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool());

    val bootstrap = new ServerBootstrap(factory);

    val allChannels = new DefaultChannelGroup

    bootstrap.setPipelineFactory(new RestPipelineFactory(handlers, allChannels));

    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);

    val serverChannel = bootstrap.bind(socketAddress);
    allChannels.add(serverChannel)

    new RestServer(() => {
      allChannels.close().awaitUninterruptibly()
      bootstrap.releaseExternalResources()
    })
  }
}
