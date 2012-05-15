package org.elasticmq

case class NodeAddress(protocol: String = "http",
                       host: String = "localhost",
                       port: Int = 9324,
                       contextPath: String = "") {
  def hostAndPort = host + ":" + port
  def fullAddress = protocol + "://" + hostAndPort + contextPath
}
