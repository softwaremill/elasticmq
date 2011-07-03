package org.elasticmq

sealed abstract class VisibilityTimeout
object DefaultVisibilityTimeout extends VisibilityTimeout
case class MillisVisibilityTimeout(millis: Long) extends VisibilityTimeout