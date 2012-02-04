package org.elasticmq.rest

import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpHeaders._
import com.weiglewilczek.slf4s.Logging
import scala.annotation.tailrec

class RestHandler(handlers: List[CheckingRequestHandlerWrapper]) extends SimpleChannelUpstreamHandler with Logging {
  private def respondWith(stringResponse: StringResponse, channel: Channel) {
    val httpResponse: HttpResponse = new DefaultHttpResponse(HTTP_1_1, OK)
    httpResponse.setContent(ChannelBuffers.copiedBuffer(stringResponse.content, CharsetUtil.UTF_8))
    httpResponse.setHeader(CONTENT_TYPE, stringResponse.contentType+"; charset=UTF-8")
    httpResponse.setStatus(HttpResponseStatus.valueOf(stringResponse.statusCode))
    setContentLength(httpResponse, httpResponse.getContent.readableBytes())

    val writeFuture = channel.write(httpResponse)
    writeFuture.addListener(ChannelFutureListener.CLOSE)
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage.asInstanceOf[HttpRequest]

    @tailrec
    def tryHandlers(toTry: List[CheckingRequestHandlerWrapper]) {
      toTry match {
        case Nil => {
          // No handler
          logger.debug("No handler found for "+request.getUri)
          sendError(ctx, NOT_FOUND)
        }
        case handler :: tail => {
          handler.canHandle(request, Map()) match {
            case None => tryHandlers(tail)
            case Some(parameters) => respondWith(handler.clientHandler.handle(request, parameters), e.getChannel)
          }
        }
      }
    }

    tryHandlers(handlers)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    val channel = e.getChannel

    logger.debug("Exception during request processing", e.getCause)

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