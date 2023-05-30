package org.elasticmq.rest.sqs


import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.sys.process._
import scala.language.postfixOps
import spray.json._

class AmazonCliTestSuite extends SqsClientServerCommunication with Matchers with TableDrivenPropertyChecks {

  val cliVersions = Table(
    "cli version",
    AWSCli(Option(System.getenv("AWS_CLI_V1_EXECUTABLE")).getOrElse("aws1")),
    AWSCli(Option(System.getenv("AWS_CLI_V2_EXECUTABLE")).getOrElse("aws"))
  )

  def createQueue(name: String)(implicit cli: AWSCli): CreateQueueResponse = {
    val result = s"""${cli.executable} sqs create-queue --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-name=$name""" !!

    result.parseJson.convertTo[CreateQueueResponse]
  }

  def getQueueUrl(name: String)(implicit cli: AWSCli): GetQueueURLResponse = {
    val result =
      s"""${cli.executable} sqs get-queue-url --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request --queue-name=$name""" !!

      result.parseJson.convertTo[GetQueueURLResponse]
  }

  def listQueues(prefix: Option[String] = None)(implicit cli: AWSCli): ListQueuesResponse = {
    val prefixStr = prefix.fold("")(s => s"--queue-name-prefix=$s")

    val result = s"""${cli.executable} sqs list-queues $prefixStr --endpoint=$ServiceEndpoint --region=us-west-1 --no-sign-request""" !!

    result.parseJson.convertTo[ListQueuesResponse]
  }

  test("should create a queue and get queue url") {

    forAll(cliVersions) { implicit version =>
      val create = createQueue("test-queue")

      val get = getQueueUrl("test-queue")

      get.QueueUrl shouldBe create.QueueUrl
    }
  }

  test("should list created queues") {

    forAll(cliVersions) { implicit version =>
      createQueue("test-queue1")
      createQueue("test-queue2")

      val get = listQueues()

      get.QueueUrls should contain allOf(s"$ServiceEndpoint/$awsAccountId/test-queue2", s"$ServiceEndpoint/$awsAccountId/test-queue1")
    }

  }


  test("should list queues with the specified prefix") {
    forAll(cliVersions) { implicit version =>
      // Given
      createQueue("aaa-test-queue1")
      createQueue("aaa-test-queue2")
      createQueue("bbb-test-queue2")

      // When
      val get = listQueues(prefix = Some("aaa"))

      get.QueueUrls.length shouldBe 2
      get.QueueUrls should contain allOf(s"$ServiceEndpoint/$awsAccountId/aaa-test-queue2", s"$ServiceEndpoint/$awsAccountId/aaa-test-queue1")
    }
  }

}

case class AWSCli(executable: String)
