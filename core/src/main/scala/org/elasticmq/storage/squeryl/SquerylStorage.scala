package org.elasticmq.storage.squeryl

import org.squeryl._
import internals.DatabaseAdapter
import PrimitiveTypeMode._
import org.elasticmq.storage.{MessageStorage, QueueStorage, Storage}
import org.elasticmq._
import com.mchange.v2.c3p0.ComboPooledDataSource

class SquerylStorage extends Storage {
  def messageStorage = new SquerylMessageStorage
  def queueStorage = new SquerylQueueStorage
}

object SquerylStorage {
  def initialize(dbAdapter: DatabaseAdapter, jdbcURL: String, driverClass: String,
                 credentials: Option[(String, String)] = None,
                 create: Boolean = true) {
    import org.squeryl.SessionFactory

    val cpds = new ComboPooledDataSource
    cpds.setDriverClass(driverClass)
    cpds.setJdbcUrl(jdbcURL)

    credentials match {
      case Some((username, password)) => {
        cpds.setUser(username)
        cpds.setPassword(password)
      }
      case _ =>
    }

    SessionFactory.concreteFactory = Some(() => Session.create(cpds.getConnection, dbAdapter))

    // TODO: do in a nicer way
    if (create) {
      transaction {
        try {
          MQSchema.create
        } catch {
          case e: Exception if e.getMessage.contains("already exists") => // do nothing
          case e => throw e
        }
      }
    }
  }

  def shutdown(drop: Boolean) {
    if (drop) {
      transaction {
        MQSchema.drop
      }
    }
  }
}

class SquerylQueueStorage extends QueueStorage {
  import MQSchema._

  def persistQueue(queue: Queue) {
    transaction {
      queues.insert(SquerylQueue.from(queue))
    }
  }

  def updateQueue(queue: Queue) {
    transaction {
      queues.update(SquerylQueue.from(queue))
    }
  }

  def deleteQueue(queue: Queue) {
    transaction {
      queues.delete(queue.name)
    }
  }

  def lookupQueue(name: String) = {
    transaction {
      queues.lookup(name).map(_.toQueue)
    }
  }

  def listQueues: Seq[Queue] = {
    transaction {
      from(queues)(q => select(q)).map(_.toQueue).toSeq
    }
  }
}

class SquerylMessageStorage extends MessageStorage {
  import MQSchema._

  def persistMessage(message: SpecifiedMessage) {
    transaction {
      messages.insert(SquerylMessage.from(message))
    }
  }

  def updateMessage(message: SpecifiedMessage) {
    transaction {
      messages.update(SquerylMessage.from(message))
    }
  }

  def updateNextDelivery(message: SpecifiedMessage, nextDelivery: MillisNextDelivery) = {
    transaction {
      val updatedCount = update(messages)(m =>
        where(m.id === message.id and m.nextDelivery === message.nextDelivery.millis)
                set(m.nextDelivery := nextDelivery.millis))

      if (updatedCount == 0) None else Some(message.copy(nextDelivery = nextDelivery))
    }
  }

  def deleteMessage(message: AnyMessage) {
    transaction {
      messages.delete(message.id)
    }
  }

  def lookupMessage(id: String) = {
    transaction {
      from(messages, queues)((m, q) => where(m.id === id and queuesToMessagesCond(m, q)) select(m, q))
              .headOption.map { case (m, q) => m.toMessage(q) }
    }
  }

  def lookupPendingMessage(queue: Queue, deliveryTime: Long) = {
    transaction {
      from(messages, queues)((m, q) =>
        where(m.queueName === queue.name and
                queuesToMessagesCond(m, q) and
                (m.nextDelivery lte deliveryTime)) select(m, q))
              .page(0, 1).headOption.map { case (m, q) => m.toMessage(q) }
    }
  }
}

private [squeryl] object MQSchema extends Schema {
  def queuesToMessagesCond(m: SquerylMessage, q: SquerylQueue) = q.id === m.queueName

  val queues = table[SquerylQueue]
  val messages = table[SquerylMessage]

  val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => queuesToMessagesCond(m, q))
  queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)
}

private[squeryl] class SquerylQueue(val id: String, val defaultVisibilityTimeout: Long) extends KeyedEntity[String] {
  def toQueue = Queue(id, VisibilityTimeout(defaultVisibilityTimeout))
}

private[squeryl] object SquerylQueue {
  def from(queue: Queue) = new SquerylQueue(queue.name, queue.defaultVisibilityTimeout.millis)
}

private[squeryl] class SquerylMessage(val id: String, val queueName: String, val content: String,
                                      val nextDelivery: Long) extends KeyedEntity[String] {
  def toMessage(q: SquerylQueue): SpecifiedMessage = Message(q.toQueue, id, content, MillisNextDelivery(nextDelivery))
}

private[squeryl] object SquerylMessage {
  def from(message: SpecifiedMessage) = {
    new SquerylMessage(message.id, message.queue.name, message.content, message.nextDelivery.millis)
  }
}
