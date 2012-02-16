package org.elasticmq.delegate

import org.elasticmq.Message

trait MessageDelegate extends MessageOperationsDelegate with Message {
  val delegate: Message

  def content = delegate.content

  def id = delegate.id

  def nextDelivery = delegate.nextDelivery

  def created = delegate.created
}
