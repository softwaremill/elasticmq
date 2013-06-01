package org.elasticmq.actor.reply

sealed trait ReplyAction[T]

case class ReplyWith[T](t: T) extends ReplyAction[T]
case class DoNotReply[T]() extends ReplyAction[T]
