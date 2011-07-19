package org.elasticmq.rest.impl

import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.handler.codec.http._

class RestHandler(handlers: List[CheckingRequestHandlerWrapper]) extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]

    val queryStringDecoder = new QueryStringDecoder(request.getUri)
    for (handler <- handlers) {
      val canHandleResult = handler.canHandle(request, queryStringDecoder)
      if (canHandleResult.isDefined) {
        handler.clientHandler.handle(request, canHandleResult.get, e.getChannel)
        return
      }
    }

    // No handler
    sendError(ctx, NOT_FOUND)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    val channel = e.getChannel

    if (channel.isConnected) {
      sendError(ctx, INTERNAL_SERVER_ERROR)
    }
  }

  private def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
    val response: HttpResponse = new DefaultHttpResponse(HTTP_1_1, status)
    response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8")
    response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString + "\r\n", CharsetUtil.UTF_8))
    ctx.getChannel.write(response).addListener(ChannelFutureListener.CLOSE)
  }
}