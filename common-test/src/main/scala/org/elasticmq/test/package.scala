package org.elasticmq

package object test {
  def timed(block: => Unit) = {
    val start = System.currentTimeMillis()
    block
    val end = System.currentTimeMillis()

    end-start
  }
}