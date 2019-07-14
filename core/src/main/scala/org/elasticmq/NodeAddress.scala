package org.elasticmq

case class NodeAddress(
    protocol: String = "http",
    host: String = "localhost",
    port: Int = 9324,
    contextPath: String = ""
) {
  def hostAndPort: String = host + ":" + port
  def fullAddress: String = protocol + "://" + hostAndPort + contextPath
  def isWildcard: Boolean = host == "*"
}
