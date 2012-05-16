package org.elasticmq.performance

import org.elasticmq.storage.StorageCommandExecutor
import org.elasticmq.{Queue, NodeBuilder}
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.storage.filelog.{FileLogConfiguration, FileLogConfigurator}
import java.io.File
import org.elasticmq.storage.squeryl.{DBConfiguration, SquerylStorage}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.model.{CreateQueueRequest, DeleteMessageRequest, ReceiveMessageRequest, SendMessageRequest}
import org.elasticmq.rest.sqs.SQSRestServerFactory
import org.elasticmq.rest.RestServer

object LocalPerformanceTest extends App {
  testAll()

  def testAll() {
    val iterations = 30
    val msgsInIteration = 10000

    //testWithMq(new InMemoryMQ, iterations, msgsInIteration, "in-memory            ")
    testWithMq(new InMemoryWithFileLogMQ, iterations, msgsInIteration, "file log + in-memory ")
    //testWithMq(new MysqlMQ, iterations, msgsInIteration,    "mysql                ")
    //testWithMq(new H2MQ, iterations, msgsInIteration,       "h2                   ")
    //testWithMq(new RestSQSMQ, iterations, msgsInIteration,  "rest-sqs + in-memory ")
  }

  def testWithMq(mq: MQ, iterations: Int, msgsInIteration: Int, name: String) {
    mq.start()

    var count = 0

    val start = System.currentTimeMillis()
    for (i <- 1 to iterations) {
      val loopStart = System.currentTimeMillis()

      for (j <- 1 to msgsInIteration) {
        mq.sendMessage("Message" + (i*j))
      }

      for (j <- 1 to msgsInIteration) {
        mq.receiveMessage()
      }

      count += msgsInIteration

      val loopEnd = System.currentTimeMillis()
      println(name + " throughput: " + (count.toDouble / ((loopEnd-start).toDouble / 1000.0)) + ", " + (loopEnd - loopStart))
    }

    mq.stop()

    println()
  }

  class InMemoryMQ extends MQWithClient {
    def createStorage() = new InMemoryStorage
  }

  class InMemoryWithFileLogMQ extends MQWithClient {
    import org.elasticmq.test._

    private var tempDir: File = _

    def createStorage() = {
      tempDir = createTempDir()
      println("Log dir: " + tempDir)
      new FileLogConfigurator(new InMemoryStorage, FileLogConfiguration(tempDir, 10000)).start()
    }

    override def stop() {
      super.stop()
      deleteDirRecursively(tempDir)
    }
  }

  class MysqlMQ extends MQWithClient {
    def createStorage() = {
      new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "", drop = true))
    }
  }

  class H2MQ extends MQWithClient {
    def createStorage() = {
      new SquerylStorage(DBConfiguration.h2())
    }
  }

  class RestSQSMQ extends MQ {
    private var currentStorage: StorageCommandExecutor = _
    private var currentSQSClient: AmazonSQSClient = _
    private var currentQueueUrl: String = _
    private var currentRestServer: RestServer = _

    def start() {
      currentStorage = new InMemoryStorage

      currentRestServer = SQSRestServerFactory.start(NodeBuilder.withStorage(currentStorage).nativeClient)

      currentSQSClient = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
      currentSQSClient.setEndpoint("http://localhost:9324")
      currentQueueUrl = currentSQSClient.createQueue(new CreateQueueRequest("testQueue")).getQueueUrl
    }

    def stop() {
      currentSQSClient.shutdown()
      currentRestServer.stop()
    }

    def sendMessage(m: String) {
      currentSQSClient.sendMessage(new SendMessageRequest(currentQueueUrl, m))
    }

    def receiveMessage() = {
      val msgs = currentSQSClient.receiveMessage(new ReceiveMessageRequest(currentQueueUrl)).getMessages
      if (msgs.size != 1) {
        throw new Exception(msgs.toString)
      }

      currentSQSClient.deleteMessage(new DeleteMessageRequest(currentQueueUrl, msgs.get(0).getReceiptHandle))

      msgs.get(0).getBody
    }
  }

  trait MQ {
    def start()

    def stop()

    def sendMessage(m: String)

    def receiveMessage(): String
  }

  trait MQWithClient extends MQ {
    def createStorage(): StorageCommandExecutor

    private var currentStorage: StorageCommandExecutor = _
    private var currentQueue: Queue = _

    def start() {
      currentStorage = createStorage()
      val client = NodeBuilder.withStorage(currentStorage).nativeClient
      currentQueue = client.createQueue("testQueue")
    }

    def stop() {
      currentStorage.shutdown()
    }

    def sendMessage(m: String) {
      currentQueue.sendMessage(m)
    }

    def receiveMessage() = {
      val message = currentQueue.receiveMessage().get
      message.delete()
      message.content
    }
  }
}
