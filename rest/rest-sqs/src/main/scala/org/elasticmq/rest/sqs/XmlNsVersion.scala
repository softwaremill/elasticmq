package org.elasticmq.rest.sqs

import akka.http.scaladsl.server.Directives._
import org.elasticmq.rest.sqs.Constants.SqsDefaultVersion
import scala.language.postfixOps

case class XmlNsVersion(version: String)

object XmlNsVersion {
  def apply(): XmlNsVersion = XmlNsVersion(SqsDefaultVersion)

  def extractXmlNs = parameter("Version" ?).map { versionOpt => versionOpt.fold(XmlNsVersion())(XmlNsVersion(_)) }

}
