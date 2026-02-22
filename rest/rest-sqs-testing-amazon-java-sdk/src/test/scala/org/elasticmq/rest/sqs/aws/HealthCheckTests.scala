package org.elasticmq.rest.sqs.aws

import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait HealthCheckTests extends AmazonJavaSdkNewTestBase with ScalatestRouteTest {

  test("health check") {
    val responseFuture = Http().singleRequest(HttpRequest(uri = s"http://localhost:9321/health"))
    val response = Await.result(responseFuture, 10.seconds)

    response.status shouldBe StatusCodes.OK

    val entityFuture = response.entity.dataBytes.runFold("")(_ ++ _.utf8String)
    val entity = Await.result(entityFuture, 10.seconds)

    entity shouldBe "OK"
  }
}
