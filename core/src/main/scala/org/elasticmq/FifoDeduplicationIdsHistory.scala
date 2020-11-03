package org.elasticmq

import com.typesafe.scalalogging.LazyLogging
import org.elasticmq.FifoDeduplicationIdsHistory.deduplicationInterval
import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

import scala.annotation.tailrec

/** Contains history of used Deduplication IDs associated with incoming messages to FIFO queues
  * @param messagesByDeduplicationId contains all registered deduplication IDs with associated messages. Used as a fast access storage for lookups if given ID was already registered
  * @param deduplicationIdsByCreationDate Deduplication IDs stored together with the message creation date.
  *                                       Incoming IDs should be already sorted by their creation date so it is safe to assume that the list will be ordered from oldest to newest.
  *                                       Used for fast lookups for messages by their creation date while cleaning outdated messages
  */
case class FifoDeduplicationIdsHistory(
    messagesByDeduplicationId: Map[DeduplicationId, InternalMessage],
    deduplicationIdsByCreationDate: List[DeduplicationIdWithCreationDate]
) extends LazyLogging {

  def addNew(message: InternalMessage): FifoDeduplicationIdsHistory = {
    message.messageDeduplicationId match {
      case Some(deduplicationId) if !messagesByDeduplicationId.contains(deduplicationId) =>
        new FifoDeduplicationIdsHistory(
          messagesByDeduplicationId + ((deduplicationId, message)),
          deduplicationIdsByCreationDate :+ DeduplicationIdWithCreationDate(deduplicationId, message.created)
        )
      case _ => this
    }
  }

  def wasRegistered(maybeDeduplicationId: Option[DeduplicationId]): Option[InternalMessage] =
    maybeDeduplicationId.flatMap(deduplicationId => messagesByDeduplicationId.get(deduplicationId))

  def cleanOutdatedMessages(nowProvider: NowProvider): FifoDeduplicationIdsHistory = {
    val (idsToRemove, notTerminatedMessages) =
      partitionMapUntil(deduplicationIdsByCreationDate)(
        map = _.id,
        cond = _.creationDate.plusMinutes(deduplicationInterval).isBefore(nowProvider.now)
      )

    if (idsToRemove.nonEmpty) {
      logger.debug(s"Removing messages from FIFO history store: $idsToRemove")
    }
    new FifoDeduplicationIdsHistory(
      messagesByDeduplicationId.removedAll(idsToRemove),
      notTerminatedMessages
    )
  }

  private def partitionMapUntil[A, B](coll: List[A])(map: A => B, cond: A => Boolean): (List[B], List[A]) = {
    @tailrec
    def go(mapppedElements: List[B], restOfTheColl: List[A]): (List[B], List[A]) = restOfTheColl match {
      case head :: tail if cond(head) => go(mapppedElements :+ map(head), tail)
      case _ :: _                     => (mapppedElements, restOfTheColl)
      case Nil                        => (mapppedElements, List.empty)
    }
    go(List.empty, coll)
  }
}

object FifoDeduplicationIdsHistory {
  val deduplicationInterval = 5

  private def apply(
      messagesByDeduplicationId: Map[DeduplicationId, InternalMessage],
      deduplicationIdsWithCreationDate: List[DeduplicationIdWithCreationDate]
  ) = new FifoDeduplicationIdsHistory(messagesByDeduplicationId, deduplicationIdsWithCreationDate)

  def newHistory(): FifoDeduplicationIdsHistory = FifoDeduplicationIdsHistory(Map.empty, List.empty)
}

case class DeduplicationIdWithCreationDate(id: DeduplicationId, creationDate: DateTime)
