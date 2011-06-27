package org.elasticmq.storage.squeryl

import org.squeryl.adapters.H2Adapter
import org.squeryl._
import org.elasticmq.{Message, Queue}
import PrimitiveTypeMode._
import org.elasticmq.storage.{MessageStorage, QueueStorage, Storage}

class SquerylStorage extends Storage {
  def messageStorage = new SquerylMessageStorage
  def queueStorage = new SquerylQueueStorage
}

object SquerylStorage {
  def initialize() {
    import org.squeryl.SessionFactory

    Thread.currentThread().getContextClassLoader.loadClass("org.h2.Driver");

    SessionFactory.concreteFactory = Some(()=>
      Session.create(
        java.sql.DriverManager.getConnection("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1"),
        new H2Adapter))

    transaction {
      MQSchema.create
    }
  }

  def shutdown() {
    transaction {
      MQSchema.drop
    }
  }
}

class SquerylQueueStorage extends QueueStorage {
  import MQSchema._

  def persistQueue(queue: Queue) {
    transaction {
      queues.insert(new SquerylQueue(queue.name))
    }
  }

  def updateQueue(queue: Queue) = null

  def removeQueue(queue: Queue) {
    transaction {
      queues.delete(queue.name)
    }
  }

  def lookupQueue(name: String) = {
    transaction {
      queues.lookup(name).map(_.toQueue)
    }
  }
}

class SquerylMessageStorage extends MessageStorage {
  import MQSchema._

  def persistMessage(message: Message) {
    transaction {
      messages.insert(new SquerylMessage(message.id, message.queue.name, message.content))
    }
  }

  def updateMessage(message: Message) = null

  def removeMessage(message: Message) {
    transaction {
      messages.delete(message.id)
    }
  }

  def lookupMessage(id: String) = {
    transaction {
      messages.lookup(id).map(_.toMessage)
    }
  }

  def lookupUndeliveredMessage(queue: Queue) = {
    transaction {
      from(messages)(m => where(m.queueName === queue.name) select(m)).page(0, 1).headOption.map(_.toMessage)
    }
  }
}

private [squeryl] object MQSchema extends Schema {
  val queues = table[SquerylQueue]
  val messages = table[SquerylMessage]

  val queuesToMessages = oneToManyRelation(queues, messages).via((q, m) => q.id === m.queueName)
  queuesToMessages.foreignKeyDeclaration.constrainReference(onDelete cascade)
}

private[squeryl] class SquerylQueue(val id: String) extends KeyedEntity[String] {
  def toQueue = Queue(id)
}

private[squeryl] class SquerylMessage(val id: String, val queueName: String, val content: String) extends KeyedEntity[String] {
  def toMessage = Message(Queue(queueName), id, content)
}
