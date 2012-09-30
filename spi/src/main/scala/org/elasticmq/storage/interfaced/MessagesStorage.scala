package org.elasticmq.storage.interfaced

import org.elasticmq._
import org.elasticmq.data.MessageData
import annotation.tailrec

trait MessagesStorage {
  def sendMessage(message: MessageData)
  def updateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery)
  def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData]
  def deleteMessage(messageId: MessageId)
  def lookupMessage(messageId: MessageId): Option[MessageData]

  def receiveMessages(deliveryTime: Long, newNextDelivery: MillisNextDelivery, maxCount: Int): List[MessageData] = {
    @tailrec
    def doReceive(count: Int, acc: List[MessageData]): List[MessageData] = {
      if (count == 0) {
        acc
      } else {
        receiveMessage(deliveryTime, newNextDelivery) match {
          case Some(messageData) => doReceive(count-1, messageData :: acc)
          case None => acc
        }
      }
    }

    doReceive(maxCount, Nil)
  }
}
