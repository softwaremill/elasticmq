package org.elasticmq.rest.sqs

import xml.NodeSeq
import Constants.IdSubParameter
import java.util.regex.Pattern

import org.elasticmq.SQSLimits

import scala.concurrent.Future

trait BatchRequestsModule {
  this: SQSLimitsModule with ActorSystemModule =>

  def batchParametersMap(prefix: String, parameters: Map[String, String]): List[Map[String, String]] = {
    val subParameters =
      BatchRequestsModule.subParametersMaps(prefix, parameters)

    val uniqueIds = subParameters.map(_.get(IdSubParameter)).toSet
    if (uniqueIds.size != subParameters.size) {
      throw new SQSException("AWS.SimpleQueueService.BatchEntryIdsNotDistinct")
    }

    SQSLimits.verifyBatchSize(uniqueIds.size, sqsLimits).fold(error => throw new SQSException(error), identity)

    subParameters
  }

  def batchRequest(prefix: String, parameters: AnyParams)(
      single: (Map[String, String], String, Int) => Future[NodeSeq]
  ): Future[NodeSeq] = {

    val messagesData = batchParametersMap(prefix, parameters)

    val futures = messagesData.zipWithIndex.map {
      case (messageData, index) => {
        val id = messageData(IdSubParameter)

        try {
          single(messageData, id, index)
        } catch {
          case e: SQSException =>
            Future {
              <BatchResultErrorEntry>
                <Id>{id}</Id>
                <SenderFault>true</SenderFault>
                <Code>{e.code}</Code>
                <Message>{e.message}</Message>
              </BatchResultErrorEntry>
            }
        }
      }
    }

    Future.sequence(futures).map(_.flatten)
  }
}

object BatchRequestsModule {

  /**
    * In the given list of parameters, lookups all parameters of the form: <code>{prefix}.{discriminator}.key=value</code>,
    * and for each discriminator builds a map of found key-value mappings.
    */
  def subParametersMaps(prefix: String, parameters: Map[String, String]): List[Map[String, String]] = {
    val subParameters = collection.mutable.Map[String, Map[String, String]]()
    val keyRegexp = (Pattern.quote(prefix) + "\\.([^.]+)\\.(.+)").r
    parameters.foreach {
      case (key, value) =>
        keyRegexp.findFirstMatchIn(key).map { keyMatch =>
          val discriminator = keyMatch.group(1)
          val subKey = keyMatch.group(2)

          val subMap =
            subParameters.getOrElse(discriminator, Map[String, String]())
          subParameters.put(discriminator, subMap + (subKey -> value))
        }
    }

    subParameters.toList.sortBy(_._1.toInt).map(_._2)
  }
}
