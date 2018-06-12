package org.elasticmq.actor.queue

import scala.collection.mutable

import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure
import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure.{Expired, Invalid}
import org.elasticmq.util.NowProvider

class ReceiveRequestAttemptCache {

  private val cache: mutable.Map[String, (Long, List[InternalMessage])] = mutable.Map.empty

  private val FIVE_MINUTES = 5 * 60 * 1000
  private val FIFTEEN_MINUTES = 15 * 60 * 1000

  def add(attemptId: String, messages: List[InternalMessage])(implicit nowProvider: NowProvider): Unit = {
    // Cache the given attempt
    cache.put(attemptId, (nowProvider.nowMillis, messages))

    // Remove attempts that are older than fifteen minutes
    // TODO: Rather than do this on every add, maybe this could be done periodically?
    clean()
  }

  def get(attemptId: String, messageQueue: MessageQueue)(
      implicit nowProvider: NowProvider): Either[ReceiveFailure, Option[List[InternalMessage]]] = {
    cache.get(attemptId) match {
      case Some((cacheTime, _)) if cacheTime + FIVE_MINUTES < nowProvider.nowMillis =>
        cache.remove(attemptId)
        Left(Expired)
      case Some((_, messages)) if !allValid(messages, messageQueue) =>
        Left(Invalid)
      case Some((_, messages)) =>
        Right(Some(messages))
      case None =>
        Right(None)
    }
  }

  /**
    * Ensure that all the given messages are still valid on the queue
    *
    * @param messages        The messages to check
    * @param messageQueue    The queue that should contain the messages
    * @return                `true` if all the given messages are still valid and present on the queue
    */
  private def allValid(messages: List[InternalMessage], messageQueue: MessageQueue): Boolean = {
    messages.forall { lastAttemptMessage =>
      messageQueue.byId.get(lastAttemptMessage.id) match {
        case None =>
          // If the message has been deleted, than this message can no longer be returned for the same request attempt.
          false
        case Some(messageOnQueue) =>
          // Verify the message hasn't been updated since the last time we've received it
          messageOnQueue.nextDelivery == lastAttemptMessage.nextDelivery
      }
    }
  }

  /**
    * Remove any keys that are older than fifteen minutes
    */
  private def clean()(implicit nowProvider: NowProvider): Unit = {
    val expiredKeys = cache.collect {
      case (oldAttemptId, (time, _)) if time + FIFTEEN_MINUTES < nowProvider.nowMillis => oldAttemptId
    }
    expiredKeys.foreach(cache.remove)
  }
}

object ReceiveRequestAttemptCache {

  sealed trait ReceiveFailure
  object ReceiveFailure {

    /**
		 * Indicates the attempt id has expired
		 */
    case object Expired extends ReceiveFailure

    /**
      * Indicates the attempt cannot be retried as a message has been deleted from the queue
      */
    case object Invalid extends ReceiveFailure
  }
}
