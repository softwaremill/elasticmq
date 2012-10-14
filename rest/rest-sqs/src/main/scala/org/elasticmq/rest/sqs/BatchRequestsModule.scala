package org.elasticmq.rest.sqs

import xml.NodeSeq
import Constants.IdSubParameter

trait BatchRequestsModule {
  this: SQSLimitsModule =>

  def batchParametersMap(prefix: String, parameters: Map[String, String]): List[Map[String, String]] = {
    val subParameters = ParametersUtil.subParametersMaps(prefix, parameters)

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
