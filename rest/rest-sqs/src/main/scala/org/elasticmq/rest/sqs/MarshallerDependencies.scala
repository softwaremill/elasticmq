package org.elasticmq.rest.sqs

import org.apache.pekko.http.scaladsl.server.Directives._
import org.elasticmq.rest.sqs.Constants.SqsDefaultVersion
import scala.language.postfixOps

case class MarshallerDependencies(protocol: AWSProtocol, xmlNsVersion: XmlNsVersion)

sealed trait AWSProtocol extends Product with Serializable

object AWSProtocol {
  case object AWSQueryProtocol extends AWSProtocol
  case object `AWSJsonProtocol1.0` extends AWSProtocol
}

case class XmlNsVersion(version: String)

object XmlNsVersion {
  def apply(): XmlNsVersion = XmlNsVersion(SqsDefaultVersion)

  def extractXmlNs = parameter("Version" ?).map { versionOpt => versionOpt.fold(XmlNsVersion())(XmlNsVersion(_)) }

}
