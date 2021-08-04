package org.elasticmq

import scala.io.Source

package object server {
  private[server] def load[T](base: Class[T], fileName: String): String = {
    Source
      .fromInputStream(base.getResourceAsStream(s"/$fileName"))
      .getLines()
      .mkString("\n")
  }
}
