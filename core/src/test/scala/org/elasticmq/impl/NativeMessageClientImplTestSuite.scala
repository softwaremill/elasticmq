package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.elasticmq._
import org.joda.time.{Duration, DateTime}
import org.elasticmq.test.DataCreationHelpers
import org.elasticmq.impl.nativeclient.NativeModule
import org.elasticmq.data.QueueData
import org.elasticmq.storage._
import scala.collection.mutable.ListBuffer

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar with DataCreationHelpers {
  val Now = 1316168602L
  val NowAsDateTime = new DateTime(1316168602L)

  test("sending a message should generate an id, properly set the next delivery and the created date") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now
    mockExecutor.findExecutedCommand[SendMessageCommand].message.nextDelivery.millis must be (expectedNextDelivery)
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
    msg.created.getMillis must be (Now)
  }

  test("sending an immediate message to a delayed queue should properly set the next delivery") {
    // Given
    val delayedSeconds = 12
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L)).copy(delay = Duration.standardSeconds(delayedSeconds))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now + delayedSeconds*1000
    mockExecutor.findExecutedCommand[SendMessageCommand].message.nextDelivery.millis must be (expectedNextDelivery)
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
  }

  test("should correctly bump a never received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(NeverReceived, 0)

    mockExecutor.returnWhenCommandClass(classOf[ReceiveMessageCommand], Some(m))
    mockExecutor.returnWhenCommandClass(classOf[GetMessageStatisticsCommand], stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(OnDateTimeReceived(NowAsDateTime), 1)

    mockExecutor.findExecutedCommand[UpdateMessageStatisticsCommand].messageStatistics must be (expectedMessageStats)

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly bump an already received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(OnDateTimeReceived(new DateTime(Now - 100000L)), 7)

    mockExecutor.returnWhenCommandClass(classOf[ReceiveMessageCommand], Some(m))
    mockExecutor.returnWhenCommandClass(classOf[GetMessageStatisticsCommand], stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(stats.approximateFirstReceive, 8)

    mockExecutor.findExecutedCommand[UpdateMessageStatisticsCommand].messageStatistics must be (expectedMessageStats)

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly set next delivery if receiving a message with default visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    mockExecutor.returnWhenCommandClass(classOf[GetMessageStatisticsCommand], MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))
    mockExecutor.returnWhenCommandClass(classOf[ReceiveMessageCommand], Some(m))

    // When
    queueOperations.receiveMessage(DefaultVisibilityTimeout)

    // Then
    mockExecutor.findExecutedCommand[ReceiveMessageCommand].newNextDelivery must be (MillisNextDelivery(Now + 123L))
  }

  test("should correctly set next delivery if receiving a message with a specific visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    mockExecutor.returnWhenCommandClass(classOf[GetMessageStatisticsCommand], MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))
    mockExecutor.returnWhenCommandClass(classOf[ReceiveMessageCommand], Some(m))

    // When
    queueOperations.receiveMessage(MillisVisibilityTimeout(1000L))

    // Then
    mockExecutor.findExecutedCommand[ReceiveMessageCommand].newNextDelivery must be (MillisNextDelivery(Now + 1000L))
  }

  def createQueueOperationsWithMockStorage(queueData: QueueData): (QueueOperations, MockStorageCommandExecutor) = {
    val env = new NativeModule
      with StorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockStorageCommandExecutor = new MockStorageCommandExecutor
      mockStorageCommandExecutor.returnWhenCommandClass(classOf[LookupQueueCommand], Some(queueData))

      def storageCommandExecutor = mockStorageCommandExecutor

      override def nowAsDateTime = NowAsDateTime
    }

    (env.nativeClient.queueOperations(queueData.name), env.mockStorageCommandExecutor)
  }

  class MockStorageCommandExecutor extends StorageCommandExecutor {    
    val executed = new ListBuffer[StorageCommand[_]]
    val commandResults = new scala.collection.mutable.HashMap[Class[_], AnyRef]

    def findExecutedCommand[T](implicit m: Manifest[T]): T = {
      val cmdOption = executed.find(cmd => m.erasure.isAssignableFrom(cmd.getClass))
      cmdOption must be ('defined)
      cmdOption.get.asInstanceOf[T]
    }
    
    def returnWhenCommandClass(cmdClass: Class[_], toReturn: AnyRef) {
      commandResults.put(cmdClass, toReturn)
    }

    def execute[R](command: StorageCommand[R]) = {
      executed += command

      commandResults.get(command.getClass).getOrElse({
        null
      }).asInstanceOf[R]
    }
  }
}