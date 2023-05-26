package org.elasticmq.rest.sqs

import scala.xml.Elem

trait XmlSerializer[T] {

  def toXml(t: T): Elem

}

object XmlSerializer {
  def apply[T](implicit instance: XmlSerializer[T]) = instance
}
