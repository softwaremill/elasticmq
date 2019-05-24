package org.elasticmq.rest.sqs.directives

import akka.http.scaladsl.server.{Directives, Route}
import org.elasticmq.rest.sqs.{ActorSystemModule, QueueManagerActorModule}
import org.elasticmq.util.Logging

trait ElasticMQDirectives
    extends Directives
    with RespondDirectives
    with FutureDirectives
    with ExceptionDirectives
    with QueueDirectives
    with QueueManagerActorModule
    with ActorSystemModule
    with AnyParamDirectives
    with RejectionDirectives
    with Logging {

  /**
    * A valid FIFO parameter value is at most 128 characters and can contain
    *  - alphanumeric characters (a-z , A-Z , 0-9 ) and
    *  - punctuation (!"#$%'()*+,-./:;=?@[\]^_`{|}~ ).
    */
  private val validFifoParameterValueCharsRe = """^[a-zA-Z0-9!"#\$%&'\(\)\*\+,-\./:;<=>?@\[\\\]\^_`\{|\}~]{1,128}$""".r

  def rootPath(body: Route): Route = {
    path("") {
      body
    }
  }

  /**
    * Valid values are alphanumeric characters and punctuation (!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~). The maximum length is
    * 128 characters
    *
    * @param propValue    The string to validate
    * @return             `true` if the string is valid, false otherwise
    */
  protected def isValidFifoPropertyValue(propValue: String): Boolean =
    validFifoParameterValueCharsRe.findFirstIn(propValue).isDefined
}
