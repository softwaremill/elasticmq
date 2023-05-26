package org.elasticmq.rest.sqs

import scala.xml.Elem

trait XmlSerializer[T] {

  def toXml(t: T): Elem

}
