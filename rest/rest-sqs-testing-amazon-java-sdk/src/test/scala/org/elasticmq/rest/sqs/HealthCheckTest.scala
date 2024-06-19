package org.elasticmq.rest.sqs
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class HealthCheckTest extends SqsClientServerCommunication with ScalatestRouteTest with Matchers {

  test("health check") {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"$ServiceEndpoint/health"))
    val response = Await.result(responseFuture, 10.seconds)

    response.status shouldBe StatusCodes.OK

    val entityFuture = response.entity.dataBytes.runFold("")(_ ++ _.utf8String)
    val entity = Await.result(entityFuture, 10.seconds)

    entity shouldBe "OK"
  }
}
