ElasticMQ
=========

[![Build Status](https://travis-ci.org/softwaremill/elasticmq.svg?branch=master)](https://travis-ci.org/softwaremill/elasticmq)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.elasticmq/elasticmq-rest-sqs_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.elasticmq/elasticmq-rest-sqs_2.11/)

tl;dr
-----

* in-memory message queue system
* runs stand-alone ([download](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-0.14.7.jar)), via [Docker](https://hub.docker.com/r/softwaremill/elasticmq/) or embedded
* [Amazon SQS](http://aws.amazon.com/sqs/)-compatible interface
* fully asynchronous implementation, no blocking calls

Created and maintained by
[<img src="https://softwaremill.com/images/header-main-logo.svg" alt="SoftwareMill logo" height="25">](https://softwaremill.com)

Summary
-------

ElasticMQ is a message queue system, offering an actor-based Scala and an [SQS](http://aws.amazon.com/sqs/)-compatible
REST (query) interface.

ElasticMQ follows the semantics of SQS. Messages are received by polling the queue.
When a message is received, it is blocked for a specified amount of time (the visibility timeout).
If the message isn't deleted during that time, it will be again available for delivery.
Moreover, queues and messages can be configured to always deliver messages with a delay.

The focus in SQS (and ElasticMQ) is to make sure that the messages are delivered.
It may happen, however, that a message is delivered twice (if, for example, a client dies after receiving a message and
processing it, but before deleting). That's why clients of ElasticMQ (and Amazon SQS) should be idempotent.

As ElasticMQ implements a subset of the [SQS](http://aws.amazon.com/sqs/) query (REST) interface, it is a great SQS
alternative both for testing purposes (ElasticMQ is easily embeddable) and for creating systems which work both within
and outside of the Amazon infrastructure.

The future will most probably bring even more exciting features :).

Community
---------

* [Issues](https://github.com/adamw/elasticmq/issues)
* Forum (discussions, help): [Google group](https://groups.google.com/forum/?fromgroups#!forum/elasticmq).
* (old) [blog](http://www.warski.org/blog/category/elasticmq/)

Installation: stand-alone
-------------------------

You can download the stand-alone distribution here:
[https://s3/.../elasticmq-server-0.14.7.jar](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-0.14.7.jar)

Java 8 or above is required for running the server.

Simply run the jar and you should get a working server, which binds to `localhost:9324`:

    java -jar elasticmq-server-0.14.7.jar

ElasticMQ uses [Typesafe Config](https://github.com/typesafehub/config) for configuration. To specify custom
configuration values, create a file (e.g. `custom.conf`), fill it in with the desired values, and pass it to the server:

    java -Dconfig.file=custom.conf -jar elasticmq-server-0.14.7.jar

The config file may contain any configuration for Akka and ElasticMQ. Current ElasticMQ configuration values are:

````
include classpath("application.conf")

// What is the outside visible address of this ElasticMQ node
// Used to create the queue URL (may be different from bind address!)
node-address {
    protocol = http
    host = localhost
    port = 9324
    context-path = ""
}

rest-sqs {
    enabled = true
    bind-port = 9324
    bind-hostname = "0.0.0.0"
    // Possible values: relaxed, strict
    sqs-limits = strict
}

// Should the node-address be generated from the bind port/hostname
// Set this to true e.g. when assigning port automatically by using port 0.
generate-node-address = false

queues {
    // See next section
}
````

You can also provide an alternative [Logback](http://logback.qos.ch/) configuration file (the
[default](server/src/main/resources/logback.xml) is configured to
log INFO logs and above to the console):

    java -Dlogback.configurationFile=my_logback.xml -jar elasticmq-server-0.14.7.jar

How are queue URLs created
--------------------------

Some of the responses include a queue URL. By default, the URLs will use `http://localhost:9324` as the base URL.
To customize, you should properly set the protocol/host/port/context in the `node-address` setting (see above).

You can also set `node-address.host` to a special value, `"*"`, which will cause any queue URLs created during a request
to use the path of the incoming request. This might be useful e.g. in containerized (Docker) deployments.

Note that changing the `bind-port` and `bind-hostname` settings do not affect the queue URLs in any way unless
`generate-node-address` is `true`. In that case, the bind host/port are used to create the node address. This is
useful when the port should be automatically assigned (use port `0` in such case, the selected port will be
visible in the logs).

Automatically creating queues on startup
----------------------------------------

Queues can be automatically created on startup by providing appropriate configuration:

The queues are specified in a custom configuration file. For example, create a `custom.conf` file with the following:

````
include classpath("application.conf")

queues {
    queue1 {
        defaultVisibilityTimeout = 10 seconds
        delay = 5 seconds
        receiveMessageWait = 0 seconds
        deadLettersQueue {
            name = "queue1-dead-letters"
            maxReceiveCount = 3 // from 1 to 1000
        }
        fifo = false
        contentBasedDeduplication = false
        copyTo = "audit-queue-name"
        moveTo = "redirect-queue-name"
        tags {
            tag1 = "tagged1"
            tag2 = "tagged2"
        }
    }
    queue1-dead-letters { }
    audit-queue-name { }
    redirect-queue-name { }
}
````

All attributes are optional (except `name` and `maxReceiveCount` when a `deadLettersQueue` is defined).
`copyTo` and `moveTo` attributes allow to achieve behavior that might be useful primarily for integration testing scenarios -
all messages could be either duplicated (using `copyTo` attribute) or redirected (using `moveTo` attribute) to another queue.

Starting an embedded ElasticMQ server with an SQS interface
-----------------------------------------------------------

    val server = SQSRestServerBuilder.start()
    // ... use ...
    server.stopAndWait()

If you need to bind to a different host/port, there are configuration methods on the builder:

    val server = SQSRestServerBuilder.withPort(9325).withInterface("localhost").start()
    // ... use ...
    server.stopAndWait()

You can also set a dynamic port with a port value of `0` or by using the method `withDynamicPort`. To retrieve the port (and other configuration) when using a dynamic port value you can access the server via `waitUntilStarted` for example:

    val server = SQSRestServerBuilder.withDynamicPort().start()
    server.waitUntilStarted().localAddress().getPort()

You can also provide a custom `ActorSystem`; for details see the javadocs.

Embedded ElasticMQ can be used from any JVM-based language (Java, Scala, etc.).

(Note that the embedded server does not load any configuration files, so you cannot automatically create queues on startup as described above. You can of course create queues programmatically.)

Using the Amazon Java SDK to access an ElasticMQ Server
-------------------------------------------------------

To use [Amazon Java SDK](http://aws.amazon.com/sdkforjava/) as an interface to an ElasticMQ server you just need
to change the endpoint:

    String endpoint = "http://localhost:9324";
    String region = "elasticmq";
    String accessKey = "x";
    String secretKey = "x";
    AmazonSQS client = AmazonSQSClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
        .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
        .build();

The endpoint value should be the same address as the `NodeAddress` provided as an argument to
`SQSRestServerBuilder` or in the configuration file.

The `rest-sqs-testing-amazon-java-sdk` module contains some more usage examples.

Using the Amazon boto (Python) to access an ElasticMQ Server
-------------------------------------------------------

To use [Amazon boto](http://docs.pythonboto.org/en/latest/) as an interface to an ElasticMQ server you set up the connection using:

    region = boto.sqs.regioninfo.RegionInfo(name='elasticmq',
                                            endpoint=sqs_endpoint)
    conn = boto.connect_sqs(aws_access_key_id='x',
                            aws_secret_access_key='x',
                            is_secure=False,
                            port=sqs_port,
                            region=region)

where `sqs_endpoint` and `sqs_port` are the host and port.

The `boto3` interface is different:

    client = boto3.resource('sqs',
                            endpoint_url='http://localhost:9324',
                            region_name='elasticmq',
                            aws_secret_access_key='x',
                            aws_access_key_id='x',
                            use_ssl=False)
    queue = client.get_queue_by_name(QueueName='queue1')

ElasticqMQ via Docker
---------------------

A Docker image is built on each release an pushed as [`softwaremill/elasticmq`](https://hub.docker.com/r/softwaremill/elasticmq/).

Run using:

```
docker run -p 9324:9324 softwaremill/elasticmq
```

The image uses default configuration. Custom configuration can be provided (e.g. to change the port, or create queues on startup) by creating a custom configuration file (see above) and using it when running the container:

```
docker run -p 9324:9324 -v `pwd`/custom.conf:/opt/elasticmq.conf softwaremill/elasticmq
```

To pass additional java system properties (`-D`) you need to prepare an `application.ini` file. For instance, to set custom `logback.xml` configuration, `application.ini` should look as follows:

```
application.ini:
-Dconfig.file=/opt/elasticmq.conf
-Dlogback.configurationFile=/opt/docker/conf/logback.xml
```

To run container with customized `application.ini` file (and custom `logback.xml` in this particular case) the following command should be used:
```
docker run -v `pwd`/application.ini:/opt/docker/conf/application.ini -v `pwd`/logback.xml:/opt/docker/conf/logback.xml -p 9324:9324 softwaremill/elasticmq
```

Another option is to use custom `Dockerfile`:

```
FROM openjdk:8-jre-alpine

ARG ELASTICMQ_VERSION
ENV ELASTICMQ_VERSION ${ELASTICMQ_VERSION:-0.14.7}

RUN apk add --no-cache curl ca-certificates
RUN mkdir -p /opt/elasticmq/log /opt/elasticmq/lib /opt/elasticmq/config
RUN curl -sfLo /opt/elasticmq/lib/elasticmq.jar https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-${ELASTICMQ_VERSION}.jar

WORKDIR /opt/elasticmq

EXPOSE 9324

ENTRYPOINT [ "/usr/bin/java", "-jar", "/opt/elasticmq/lib/elasticmq.jar" ]
```

and override the entrypoint passing the required properties.

Experimental native ElasticqMQ via Docker
-----------------------------------------

An experimental, dockerized version of ElasticMQ, 
built using GraalVM's [native-image](https://blog.softwaremill.com/small-fast-docker-images-using-graalvms-native-image-99c0bc92e70b),
is available as [`softwaremill/elasticmq-native`](https://hub.docker.com/r/softwaremill/elasticmq-native/). To start, run:

```
docker run -p 9324:9324 --rm -it softwaremill/elasticmq-native
```

The `native-elasticmq` image is much smaller (30MB vs 240MB) and starts up much faster (milliseconds instead of seconds).
However, it's an experimental feature, so some things might not work.

ElasticMQ dependencies in SBT
-----------------------------

    // Scala 2.12 and 2.11
    val elasticmqSqs        = "org.elasticmq" %% "elasticmq-rest-sqs" % "0.14.7"

If you don't want the SQS interface, but just use the actors directly, you can add a dependency only to the `core`
module:

    val elasticmqCore       = "org.elasticmq" %% "elasticmq-core" % "0.14.7"

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-rest-sqs_2.11</artifactId>
        <version>0.14.7</version>
    </dependency>

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

Current versions
----------------

*Stable*: 0.14.7, 0.8.12

*Development*: 0.14.7-SNAPSHOT

Logging
-------

ElasticMQ uses [Slf4j](http://www.slf4j.org/) for logging. By default no logger backend is included as a dependency,
however [Logback](http://logback.qos.ch/) is recommended.

Performance
-----------

Tests done on a 2012 MBP, 2.6GHz, 16GB RAM, no replication. Throughput is in messages per second (messages are
small).

Directly accessing the client:

    Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 1.
    Overall in-memory throughput: 21326.054040

    Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 2.
    Overall in-memory throughput: 26292.956117

    Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 10.
    Overall in-memory throughput: 25591.155697

Through the SQS REST interface:

    Running test for [rest-sqs + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 20.
    Overall rest-sqs + in-memory throughput: 2540.553587

    Running test for [rest-sqs + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 40.
    Overall rest-sqs + in-memory throughput: 2600.002600

Note that both the client and the server were on the same machine.

Test class: `org.elasticmq.performance.LocalPerformanceTest`.

Building, running, and packaging
--------------------------------

To build and run with debug (this will listen for a remote debugger on port 5005):
```
~/workspace/elasticmq $ sbt -jvm-debug 5005
> project server
> run
```

To build a jar-with-dependencies:
```
~/workspace/elasticmq $ sbt
> project server
> assembly
```

Tests and coverage
------------------

To run the tests:
```
~/workspace/elasticmq $ sbt test
```

To check the coverage reports:
```
~/workspace/elasticmq $ sbt
> coverage
> tests
> coverageReport
> coverageAggregate
```

Although it's mostly only the core project that is relevant for coverage testing, each project's report can be found
in their target directory:

 * core/target/scala-2.12/scoverage-report/index.html
 * common-test/target/scala-2.12/scoverage-report/index.html
 * rest/rest-sqs/target/scala-2.12/scoverage-report/index.html
 * server/target/scala-2.12/scoverage-report/index.html

The aggregate report can be found at target/scala-2.12/scoverage-report/index.html

Technology
----------

* Core: [Scala](http://scala-lang.org) and [Akka](http://akka.io/).
* Rest server: [Akka HTTP](http://doc.akka.io/docs/akka-http/current/), a high-performance,
  asynchronous, REST/HTTP toolkit.
* Testing the SQS interface: [Amazon Java SDK](http://aws.amazon.com/sdkforjava/);
  see the `rest-sqs-testing-amazon-java-sdk` module for the testsuite.
