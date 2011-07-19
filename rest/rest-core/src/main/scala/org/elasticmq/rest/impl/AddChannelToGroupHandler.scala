package org.elasticmq.rest.impl

import org.jboss.netty.channel.{ChannelStateEvent, ChannelHandlerContext, SimpleChannelUpstreamHandler}
import org.jboss.netty.channel.group.ChannelGroup

class AddChannelToGroupHandler(group: ChannelGroup) extends SimpleChannelUpstreamHandler {
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    group.add(e.getChannel)
  }
}