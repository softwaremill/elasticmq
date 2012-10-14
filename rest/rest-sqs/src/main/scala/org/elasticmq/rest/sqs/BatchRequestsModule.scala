package org.elasticmq.rest.sqs

import xml.NodeSeq
import Constants.IdSubParameter
import java.util.regex.Pattern

trait BatchRequestsModule {
  this: SQSLimitsModule =>

  def batchParametersMap(prefix: String, parameters: Map[String, String]): List[Map[String, String]] = {
    val subParameters = BatchRequestsModule.subParametersMaps(prefix, parameters)

    val uniqueIds = subParameters.map(_.get(IdSubParameter)).toSet
    if (uniqueIds.size != subParameters.size) {
      throw new SQSException("AWS.SimpleQueueService.BatchEntryIdsNotDistinct")
    }

    ifStrictLimits(uniqueIds.size > 10) {
      "AWS.SimpleQueueService.TooManyEntriesInBatchRequest"
    }

    subParameters
  }

  def batchRequest(prefix: String, parameters: Map[String, String])(single: (Map[String, String], String) => NodeSeq) = {
    val messagesData = batchParametersMap(prefix, parameters)

    messagesData.map(messageData => {
      val id = messageData(IdSubParameter)

      try {
        single(messageData, id)
      } catch {
        case e: SQSException => {
          <BatchResultErrorEntry>
            <Id>{id}</Id>
            <SenderFault>true</SenderFault>
            <Code>{e.code}</Code>
            <Message>{e.message}</Message>
          </BatchResultErrorEntry>
        }
      }
    })
  }
}

object BatchRequestsModule {
  /**
   * In the given list of parameters, lookups all parameters of the form: <code>{prefix}.{discriminator}.key=value</code>,
   * and for each discriminator builds a map of found key-value mappings.
   */
  def subParametersMaps(prefix: String, parameters: Map[String, String]): List[Map[String, String]] = {
    val subParameters = collection.mutable.Map[String, Map[String, String]]()
    val keyRegexp = (Pattern.quote(prefix) + "\\.(.+)\\.(.+)").r
    parameters.foreach{ case (key, value) =>
      keyRegexp.findFirstMatchIn(key).map { keyMatch =>
        val discriminator = keyMatch.group(1)
        val subKey = keyMatch.group(2)

        val subMap = subParameters.get(discriminator).getOrElse(Map[String, String]())
        subParameters.put(discriminator, subMap + (subKey -> value))
      }
    }

    subParameters.values.map(_.toMap).toList
  }
}
