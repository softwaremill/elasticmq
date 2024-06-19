package org.elasticmq.rest.sqs
import org.scalatest.matchers.should.Matchers

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

class HealthCheckTest extends SqsClientServerCommunication with Matchers {

  test("health check") {
    val client = HttpClient.newHttpClient
    val request = HttpRequest.newBuilder.uri(URI.create(s"$ServiceEndpoint/health")).GET.build
    val response = client.send(request, HttpResponse.BodyHandlers.ofString)

    response.statusCode shouldBe 200
    response.body() shouldBe "OK"
  }
}
