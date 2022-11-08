package org.elasticmq

case class NodeAddress(
    protocol: String = "http",
    host: String = "localhost",
    port: Int = 9324,
    contextPath: String = ""
) {
  def hostAndPort: String = host + ":" + port
  def fullAddress: String = protocol + "://" + hostAndPort + suffix
  def isWildcard: Boolean = host == "*"
  def contextPathStripped: String = contextPath.stripPrefix("/").stripSuffix("/")
  def suffix: String = if (contextPath.trim.isEmpty) "" else "/" + contextPathStripped
}
