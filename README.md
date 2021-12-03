![ElasticMQ](https://github.com/softwaremill/elasticmq/raw/master/banner.png)

[![ CI ](https://github.com/softwaremill/elasticmq/workflows/ElasticMQ%20tests/badge.svg)](https://github.com/softwaremill/elasticmq/actions?query=workflow%3A%22ElasticMQ+tests%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.elasticmq/elasticmq-rest-sqs_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.elasticmq/elasticmq-rest-sqs_2.12/)

# tl;dr

* in-memory message queue system
* runs stand-alone ([download](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-1.3.3.jar)), via [Docker](https://hub.docker.com/r/softwaremill/elasticmq-native/) or embedded
* [Amazon SQS](http://aws.amazon.com/sqs/)-compatible interface
* fully asynchronous implementation, no blocking calls
* optional UI, queue persistence
* created and maintained by:

<p align="center">
  <a href="https://softwaremill.com"><img src="https://files.softwaremill.com/logo/logo.png" alt="SoftwareMill logo" height="100"></a>
</p>

# Summary

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

A simple UI is available for viewing real-time queue statistics.

# Community

* [Issues](https://github.com/adamw/elasticmq/issues)

# Installation: stand-alone

You can download the stand-alone distribution here:
[https://s3/.../elasticmq-server-1.3.3.jar](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-1.3.3.jar)

Java 8 or above is required for running the server.

Simply run the jar and you should get a working server, which binds to `localhost:9324`:
    
```
java -jar elasticmq-server-1.3.3.jar
```

ElasticMQ uses [Typesafe Config](https://github.com/typesafehub/config) for configuration. To specify custom
configuration values, create a file (e.g. `custom.conf`), fill it in with the desired values, and pass it to the server:

```
java -Dconfig.file=custom.conf -jar elasticmq-server-1.3.3.jar
```

The config file may contain any configuration for Akka and ElasticMQ. Current ElasticMQ configuration values are:

```
include classpath("application.conf")

# What is the outside visible address of this ElasticMQ node
# Used to create the queue URL (may be different from bind address!)
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
  # Possible values: relaxed, strict
  sqs-limits = strict
}

rest-stats {
  enabled = true
  bind-port = 9325
  bind-hostname = "0.0.0.0"
}

# Should the node-address be generated from the bind port/hostname
# Set this to true e.g. when assigning port automatically by using port 0.
generate-node-address = false

queues {
  # See next sections
}

queues-storage {
  # See next sections
}

# Region and accountId which will be included in resource ids
aws {
  region = us-west-2
  accountId = 000000000000
}
```

You can also provide an alternative [Logback](http://logback.qos.ch/) configuration file (the
[default](server/src/main/resources/logback.xml) is configured to
log INFO logs and above to the console):
                 
```
java -Dlogback.configurationFile=my_logback.xml -jar elasticmq-server-1.3.3.jar
```

# How are queue URLs created

Some of the responses include a queue URL. By default, the URLs will use `http://localhost:9324` as the base URL.
To customize, you should properly set the protocol/host/port/context in the `node-address` setting (see above).

You can also set `node-address.host` to a special value, `"*"`, which will cause any queue URLs created during a request
to use the path of the incoming request. This might be useful e.g. in containerized (Docker) deployments.

Note that changing the `bind-port` and `bind-hostname` settings do not affect the queue URLs in any way unless
`generate-node-address` is `true`. In that case, the bind host/port are used to create the node address. This is
useful when the port should be automatically assigned (use port `0` in such case, the selected port will be
visible in the logs).

# Automatically creating queues on startup

Queues can be automatically created on startup by providing appropriate configuration:

The queues are specified in a custom configuration file. For example, create a `custom.conf` file with the following:

````
# the include should be done only once, at the beginning of the custom configuration file
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

While creating the FIFO queue, .fifo suffix will be added automatically to queue name.

# Persisting queues configuration

Queues configuration can be persisted in an external config file in the [HOCON](https://en.wikipedia.org/wiki/HOCON) 
format. Note that only the queue metadata (which queues are created, and with what attributes) will be stored, without 
any messages.

To enable the feature, create a custom configuration file with the following content:

````
# the include should be done only once, at the beginning of the custom configuration file
include classpath("application.conf")

queues-storage {
  enabled = true
  path = "/path/to/storage/queues.conf"
}
````

Any time a queue is created, deleted, or its metadata change, the given file will be updated. 

On startup, any queues defined in the given file will be created. Note that the persisted queues configuration takes 
precedence over queues defined in the main configuration file (as described in the previous section) in the `queues`
section.

# Persisting queues and messages to SQL database

Queues and their messages can be persisted to SQL database in runtime.
All events like queue or message creation, deletion or update will be stored in H2 in-file database,
so that the entire ElasticMQ state can be restored after server restart.

To enable the feature, create a custom configuration file with the following content:

````
# the include should be done only once, at the beginning of the custom configuration file
include classpath("application.conf")

messages-storage {
  enabled = true
}
````

By default, the database file is stored in `/data/elasticmq.db`. In order to change it,
custom JDBC uri needs to be provided:

````
# the include should be done only once, at the beginning of the custom configuration file
include classpath("application.conf")

messages-storage {
  enabled = true
  uri = "jdbc:h2:/home/me/elasticmq"
}
````

On startup, any queues and their messages persisted in the database will be recreated.
Note that the persisted queues take precedence over the queues defined
in the main configuration file (as described in the previous section) in the `queues` section.

# Starting an embedded ElasticMQ server with an SQS interface

Add ElasticMQ Server to `build.sbt` dependencies

```scala
libraryDependencies += "org.elasticmq" %% "elasticmq-server" % "1.3.3"
```

Simply start the server using custom configuration (see examples above):

```scala
val config = ConfigFactory.load("elasticmq.conf")
val server = new ElasticMQServer(new ElasticMQServerConfig(config))
server.start()
```

Alternatively, custom rest server can be built using `SQSRestServerBuilder` provided in `elasticmq-rest-sqs` package:

```scala
val server = SQSRestServerBuilder.start()
// ... use ...
server.stopAndWait()
```

If you need to bind to a different host/port, there are configuration methods on the builder:

```scala
val server = SQSRestServerBuilder.withPort(9325).withInterface("localhost").start()
// ... use ...
server.stopAndWait()
```

You can also set a dynamic port with a port value of `0` or by using the method `withDynamicPort`. To retrieve the port (and other configuration) when using a dynamic port value you can access the server via `waitUntilStarted` for example:

```scala
val server = SQSRestServerBuilder.withDynamicPort().start()
server.waitUntilStarted().localAddress().getPort()
```

You can also provide a custom `ActorSystem`; for details see the javadocs.

Embedded ElasticMQ can be used from any JVM-based language (Java, Scala, etc.).

(Note that the embedded server created with `SQSRestServerBuilder` does not load any configuration files, so you cannot automatically create queues on startup as described above. You can of course create queues programmatically.)

# Using the Amazon Java SDK to access an ElasticMQ Server

To use [Amazon Java SDK](http://aws.amazon.com/sdkforjava/) as an interface to an ElasticMQ server you just need
to change the endpoint:
                      
```java
String endpoint = "http://localhost:9324";
String region = "elasticmq";
String accessKey = "x";
String secretKey = "x";
AmazonSQS client = AmazonSQSClientBuilder.standard()
    .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
    .build();
```    

The endpoint value should be the same address as the `NodeAddress` provided as an argument to
`SQSRestServerBuilder` or in the configuration file.

The `rest-sqs-testing-amazon-java-sdk` module contains some more usage examples.

# Using the Amazon boto (Python) to access an ElasticMQ Server

To use [Amazon boto](http://docs.pythonboto.org/en/latest/) as an interface to an ElasticMQ server you set up the connection using:
        
```python
region = boto.sqs.regioninfo.RegionInfo(name='elasticmq',
                                        endpoint=sqs_endpoint)
conn = boto.connect_sqs(aws_access_key_id='x',
                        aws_secret_access_key='x',
                        is_secure=False,
                        port=sqs_port,
                        region=region)
```

where `sqs_endpoint` and `sqs_port` are the host and port.

The `boto3` interface is different:

```python
client = boto3.resource('sqs',
                        endpoint_url='http://localhost:9324',
                        region_name='elasticmq',
                        aws_secret_access_key='x',
                        aws_access_key_id='x',
                        use_ssl=False)
queue = client.get_queue_by_name(QueueName='queue1')
```

# ElasticMQ via Docker

A Docker image built using GraalVM's [native-image](https://blog.softwaremill.com/small-fast-docker-images-using-graalvms-native-image-99c0bc92e70b),
is available as [`softwaremill/elasticmq-native`](https://hub.docker.com/r/softwaremill/elasticmq-native/). 

To start, run (9324 is the default REST-SQS API port; 9325 is the default UI port, exposing it is fully optional):

```
docker run -p 9324:9324 -p 9325:9325 softwaremill/elasticmq-native
```

The `elasticmq-native` image is much smaller (30MB vs 240MB) and starts up much faster (milliseconds instead of seconds),
comparing to the full JVM version (see below).  Custom configuration can be provided by creating a custom
configuration file (see above) and using it when running the container:

```
docker run -p 9324:9324 -p 9325:9325 -v `pwd`/custom.conf:/opt/elasticmq.conf softwaremill/elasticmq-native
```

If messages storage is enabled, the directory containing database files can also be mapped:

```
docker run -p 9324:9324 -p 9325:9325 -v `pwd`/custom.conf:/opt/elasticmq.conf -v `pwd`/data:/data softwaremill/elasticmq-native
```

It is possible to specify custom `logback.xml` config as well to enable additional debug logging for example.
Some logback features, like console coloring, will not work due to missing classes in the native image. This can only be solved by building a custom image.

```
docker run -p 9324:9324 -p 9325:9325 -v `pwd`/custom.conf:/opt/elasticmq.conf -v `pwd`/logback.xml:/opt/logback.xml softwaremill/elasticmq-native
```

As for now to run `elasticmq-native` docker image on ARM based CPU one have to install `Qemu` docker for `amd64`.

```
docker run --privileged --rm tonistiigi/binfmt --install amd64
```

# ElasticMQ via Docker (full JVM)

A Docker image is built on each release an pushed as [`softwaremill/elasticmq`](https://hub.docker.com/r/softwaremill/elasticmq/). 
Run using:

```
docker run -p 9324:9324 -p 9325:9325 softwaremill/elasticmq
```

The image uses default configuration. Custom configuration can be provided (e.g. to change the port, or create queues on startup) by creating a custom configuration file (see above) and using it when running the container:

```
docker run -p 9324:9324 -p 9325:9325 -v `pwd`/custom.conf:/opt/elasticmq.conf softwaremill/elasticmq
```

If messages storage is enabled, the directory containing database files can also be mapped:

```
docker run -p 9324:9324 -p 9325:9325 -v `pwd`/custom.conf:/opt/elasticmq.conf -v `pwd`/data:/data softwaremill/elasticmq
```

To pass additional java system properties (`-D`) you need to prepare an `application.ini` file. For instance, to set custom `logback.xml` configuration, `application.ini` should look as follows:

```
application.ini:
-Dconfig.file=/opt/elasticmq.conf
-Dlogback.configurationFile=/opt/docker/conf/logback.xml
```

To run container with customized `application.ini` file (and custom `logback.xml` in this particular case) the following command should be used:
```
docker run -v `pwd`/application.ini:/opt/docker/conf/application.ini -v `pwd`/logback.xml:/opt/docker/conf/logback.xml -p 9324:9324 -p 9325:9325 softwaremill/elasticmq
```

Another option is to use custom `Dockerfile`:

```
FROM openjdk:8-jre-alpine

ARG ELASTICMQ_VERSION
ENV ELASTICMQ_VERSION ${ELASTICMQ_VERSION:-1.3.3}

RUN apk add --no-cache curl ca-certificates
RUN mkdir -p /opt/elasticmq/log /opt/elasticmq/lib /opt/elasticmq/config
RUN curl -sfLo /opt/elasticmq/lib/elasticmq.jar https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-${ELASTICMQ_VERSION}.jar

COPY ${PWD}/elasticmq.conf /opt/elasticmq/config/elasticmq.conf

WORKDIR /opt/elasticmq

EXPOSE 9324

ENTRYPOINT [ "/usr/bin/java", "-Dconfig.file=/opt/elasticmq/conf/elasticmq.conf", "-jar", "/opt/elasticmq/lib/elasticmq.jar" ]
```

and override the entrypoint passing the required properties.

# ElasticMQ dependencies in SBT
                    
```scala
// Scala 2.13 and 2.12
val elasticmqSqs        = "org.elasticmq" %% "elasticmq-rest-sqs" % "1.3.3"
```

If you don't want the SQS interface, but just use the actors directly, you can add a dependency only to the `core`
module:
    
```scala
val elasticmqCore       = "org.elasticmq" %% "elasticmq-core" % "1.3.3"
```

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

# ElasticMQ dependencies in Maven

Dependencies:
    
```xml
<dependency>
    <groupId>org.elasticmq</groupId>
    <artifactId>elasticmq-rest-sqs_2.12</artifactId>
    <version>1.3.3</version>
</dependency>
```

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

# Current versions

*Stable*: 1.3.3

# Logging

ElasticMQ uses [Slf4j](http://www.slf4j.org/) for logging. By default no logger backend is included as a dependency,
however [Logback](http://logback.qos.ch/) is recommended.

# Performance

Tests done on a 2012 MBP, 2.6GHz, 16GB RAM, no replication. Throughput is in messages per second (messages are
small).

Directly accessing the client:

```
Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 1.
Overall in-memory throughput: 21326.054040

Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 2.
Overall in-memory throughput: 26292.956117

Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 10.
Overall in-memory throughput: 25591.155697
```

Through the SQS REST interface:

```
Running test for [rest-sqs + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 20.
Overall rest-sqs + in-memory throughput: 2540.553587

Running test for [rest-sqs + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 40.
Overall rest-sqs + in-memory throughput: 2600.002600
```

Note that both the client and the server were on the same machine.

Test class: `org.elasticmq.performance.LocalPerformanceTest`.

# Building, running, and packaging

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

## Building the native image

Do not forget to adjust the CPU and memory settings for the Docker process. It was checked with 6CPUs, 8GB of memory and 2GB of swap. Also, make sure that you are running sbt with the graalvm java, as the way the jars are composed seem to differ from other java implementations, and affect the native-image process that is run later! To rebuild the native image, run:

```
sbt "project nativeServer; clean; assembly; graalvm-native-image:packageBin; docker:publishLocal"
```

Generating GraalVM config files is a manual process currently. You need to run the fat-jar using the GraalVM VM (w/ native-image installed using `gu`), and then run the following commands to generate the configs:

* `java -agentlib:native-image-agent=config-output-dir=... -jar elasticmq-server-assembly.jar`
* `java -agentlib:native-image-agent=config-merge-dir=... -Dconfig.file=test.conf -jar elasticmq-server-assembly.jar` (to additionally generate config needed to load custom elasticmq config)

These files should be placed in `native-server/src/main/resources/META-INF/native-image` and are automatically used by the native-image process.

In case of issues with running GraalVM with `native-image-agent` it's possible to execute above commands inside of docker container (the image is generated by the sbt command above).
`graalVmVersion` is defined in `build.sbt`:

```
docker run -it -v `pwd`:/opt/graalvm --entrypoint /bin/bash --rm ghcr.io-graalvm-graalvm-ce-native-image:java11-${graalVmVersion}
```

## Building multi-architecture image

Publishing Docker image for two different platforms: `amd64` and `arm64` is possible with Docker Buildx plugin.
Docker Buildx is included in Docker Desktop and Docker Linux packages when installed using the DEB or RPM packages. `build.sbt` has following setup:

* `dockerBuildxSettings` creates Docker Buildx instance
* Docker base image is `openjdk:11-jdk-stretch` which supports multi-arch images
* `dockerBuildCommand` is extended with operator `buildx`
* `dockerBuildOptions` has two additional parameters: `--platform=linux/arm64,linux/amd64` and `--push`

For the native server configuration is the same apart from Docker base image.

Parameter `--push` is very crucial. Since `docker buildx build` subcommand is not storing the resulting image in the local `docker image` list, we need that flag to determine where the final image will be stored.
Flag `--load` makes output destination of type docker. However, this currently works only for single architecture images. Therefore, both sbt commands - `docker:publishLocal` and `docker:publish` are pushing images to a Docker registry.

To change this - switch parameters for `dockerBuildOptions`:

* from `--push` to `--load` and
* from `--platform=linux/arm64,linux/amd64` to `--platform=linux/amd64`

To build images locally:

* switch sbt to module server - `sbt project server` (or `sbt project nativeServer` for module native-server)
* make sure Docker Buildx is running `docker buildx version`
* create Docker Buildx instance `docker buildx create --use --name multi-arch-builder`
* generate the Dockerfile executing `sbt docker:stage` - it will be generated in `server/target/docker/stage`
* generate multi-arch image and push it to Docker Hub:
```
docker buildx build --platform=linux/arm64,linux/amd64 --push -t softwaremill/elasticmq .
```
* or generate single-arch image and load it to docker images locally:
```
docker buildx build --platform=linux/amd64 --load -t softwaremill/elasticmq .
```


# Tests and coverage

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

# UI

![ElasticMQ-UI](ui.png)

UI provides real-time information about the state of messages and attributes of queue.

### Using UI in docker image

UI is bundled with both standard and native images. It is exposed on the address that is defined in rest-stats configuration (by default 0:0:0:0:9325).

In order to turn it off, you have to switch it off via rest-stats.enabled flag.

### Using UI locally

You can start UI via `yarn start` command in the `ui` directory, which will run on localhost:3000 address.

# MBeans

ElasticMQ exposes `Queues` MBean. It contains three operations:
* `QueueNames` - returns array of names of queues
* `NumberOfMessagesForAllQueues` - returns tabular data that contains information about number of messages per queue
* `getNumberOfMessagesInQueue` - returns information about number of messages in specified queue

# Technology

* Core: [Scala](http://scala-lang.org) and [Akka](http://akka.io/).
* Rest server: [Akka HTTP](http://doc.akka.io/docs/akka-http/current/), a high-performance,
  asynchronous, REST/HTTP toolkit.
* Testing the SQS interface: [Amazon Java SDK](http://aws.amazon.com/sdkforjava/);
  see the `rest-sqs-testing-amazon-java-sdk` module for the testsuite.

# Commercial Support

We offer commercial support for ElasticMQ and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

# Copyright

Copyright (C) 2011-2021 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
