ElasticMQ
=========

tl;dr
-----

* message queue system
* runs stand-alone ([download](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-0.14.2.jar)) or embedded
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

* [Blog](http://www.warski.org/blog/category/elasticmq/)
* Forum (discussions, help): [Google group](https://groups.google.com/forum/?fromgroups#!forum/elasticmq).

Installation: stand-alone
-------------------------

You can download the stand-alone distribution here:
[https://s3/.../elasticmq-server-0.14.2.jar](https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-0.14.2.jar)

Java 8 or above is required for running the server.

Simply run the jar and you should get a working server, which binds to `localhost:9324`:

    java -jar elasticmq-server-0.14.2.jar

ElasticMQ uses [Typesafe Config](https://github.com/typesafehub/config) for configuration. To specify custom
configuration values, create a file (e.g. `custom.conf`), fill it in with the desired values, and pass it to the server:

    java -Dconfig.file=custom.conf -jar elasticmq-server-0.14.2.jar

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

    java -Dlogback.configurationFile=my_logback.xml -jar elasticmq-server-0.14.2.jar

How are queue URLs created
--------------------------

Some of the responses include a queue URL. By default the urls will use `http://localhost:9324` as the base URL.
To customize, you should properly set the protocol/host/port/context in the `node-address` setting (see above).

You can also set `node-address.host` to a special value, `"*"`, which will cause any queue URLs created during a request
to use the path of the incoming request. This might be useful e.g. in containerized (Docker) deployments.

Note that changing the `bind-port` and `bind-hostname` settings does not affect the queue URLs in any way unless
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

ElasticMQ dependencies in SBT
-----------------------------

    // Scala 2.12 and 2.11
    val elasticmqSqs        = "org.elasticmq" %% "elasticmq-rest-sqs" % "0.14.2"

If you don't want the SQS interface, but just use the actors directly, you can add a dependency only to the `core`
module:

    val elasticmqCore       = "org.elasticmq" %% "elasticmq-core" % "0.14.2"

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-rest-sqs_2.11</artifactId>
        <version>0.14.2</version>
    </dependency>

If you want to use a snapshot version, you will need to add the [https://oss.sonatype.org/content/repositories/snapshots/](https://oss.sonatype.org/content/repositories/snapshots/) repository to your configuration.

Replication, journaling, SQL backend
------------------------------------

Until version 0.7.0, ElasticMQ included optional replication, journaling and an SQL message storage. These modules
have been discontinued.

Current versions
----------------

*Stable*: 0.14.2, 0.8.12

*Development*: 0.14.2-SNAPSHOT

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
> project elasticmq-server
> run
```

To build a jar-with-dependencies:
```
~/workspace/elasticmq $ sbt
> project elasticmq-server
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

Change log
----------

### Version 0.14.2 (24 Jul 2018) ###

* move/copy queue support by @sbinq

### Version 0.14.1 (20 Jun 2018) ###

* remove println-debugging

### Version 0.14.0 (19 Jun 2018) ###

* FIFO queues support by @simong

### Version 0.13.11 (8 Jun 2018) ###

* bug fixes
* depedency updates

### Version 0.13.10 (16 May 2018) ###

* bug fixes

### Version 0.13.9 (13 March 2018) ###

* bug fixes

### Version 0.13.8 (25 June 2017) ###

* bug fixes

### Version 0.13.7 (24 June 2017) ###

* bug fixes

### Version 0.13.6 (22 June 2017) ###

* bug fixes

### Version 0.13.5 (26 May 2017) ###

* bug fixes

#### Version 0.13.4 (16 May 2017)

* fix akka dependencies
* properly returned only requrested attributes

#### Version 0.13.3 (5 May 2017)

* fix queue creation order in presence of DLQs
* update dependencies, akka & akka-http

#### Version 0.13.2 (7 Feb 2017)

* bug fix

#### Version 0.13.1 (26 Jan 2017)

* add dummy add permission endpoint

#### Version 0.13.0 (25 Jan 2017)

* add dead letter queue support (thx @mkorolyov)

#### Version 0.12.1 (13 Dec 2016)

* remove TODOs which caused problems with .NET client

#### Version 0.12.0 (7 Dec 2016)

* support for dynamic port allocation
* node address is generated from bind address if not specified

#### Version 0.11.1 (30 Nov 2016)

* bug fix

#### Version 0.11.0 (23 Nov 2016)

* updating dependencies, using Akka HTTP 10, builds for Scala 2.11 and 2.12

#### Version 0.10.1 (31 Oct 2016)

* fixing a bug with changing message visibility and long pooling

#### Version 0.10.0 (22 Sep 2016)

* updating Akka and other dependencies

#### Version 0.9.3 (13 Apr 2016)

* bug fix

#### Version 0.9.2 (8 Apr 2016)

* fixes handling of wait time seconds equal to 0 when receiving messages

#### Version 0.9.1 (4 Apr 2016)

* fixed bug to allow connecting using Perl client

#### Version 0.9.0 (23 Mar 2016)

* replace Spray with Akka
* increase message body size limits
* provide an option to create queues on startup
* add a special node-address setting: `*`, which uses the incoming request url to create queue urls

#### Version 0.8.12 (30 Sep 2015)

* checking queue length if limits are strict

#### Version 0.8.11 (3 Sep 2015)

* downgrading typesafe-config to keep Java6 compatibility

#### Version 0.8.10 (3 Sep 2015)

* numeric attributes support (thx @sf-git)

#### Version 0.8.9 (10 Aug 2015)

* binary attributes support (thx @brainoutsource)
* dependency updates

#### Version 0.8.8 (10 Apr 2015)

* dependency updates, bug fixes

#### Version 0.8.6, 0.8.7 (7 Feb 2015, 13 Feb 2015)

* adding support for the `QueueArn` attribute

#### Version 0.8.5 (11 Dec 2014)

* supporting `PurgeQueue` action instead of a custom one

#### Version 0.8.4 (2 Dec 2014)

* custom action for clearing all messages from a queue

#### Version 0.8.3 (22 Oct 2014)

* bug fixes
* updating dependencies
* publishing to Maven Central

#### Version 0.8.2 (6 Jun 2014)

* increasing the bind timeout
* initial support for `String`-valued message attributes (thx @hayesgm)

#### Version 0.8.1 (29 May 2014)

* fixing Node.JS compatibility
* fixing a bug when calculating queue attributes
* updating to Scala 2.11.1, Akka 2.3.3

#### Version 0.8.0 (29 April 2014)

* updating to Scala 2.11, Akka 2.3.2
* updating libraries to latest versions

#### Version 0.7.1 (22 August 2013)

* bug fixes

#### Version 0.7.0 (5 June 2013)

* reimplemented using Akka and Spray (actor-based, no blocking)
* long polling support
* bug fixes

#### Version 0.6.3 (21 January 2013)

* Scala 2.10 support
* Changing the way the stand-alone server is configured

#### Version 0.6.2 (13 December 2012)

* bug fixes
* properly handling SQS receipt handles - message can be deleted only when passing the most recent receipt handle

#### Version 0.6.1 (18 November 2012)

* using Sonatype's OSS repositories for releases
* library upgrades

#### Version 0.6 (19 October 2012)

* batch operations in SQS (send, receive, delete, change visibility)
* changed `SQSRestServerFactory` to `SQSRestServerBuilder`
* "strict" and "relaxed" modes when creating an SQS server: the limits enforced by SQS are optionally checked, e.g. for
batch operations (max 10 messages), maximum message size (64KB). Strict by default.

#### Version 0.5 (26 May 2012)

* stand-alone distribution ([download](https://github.com/downloads/adamw/elasticmq/elasticmq-0.5.tar.gz))
* file log for message storage (journal)
* factoring out `storage-database` module, to decrease the dependencies of the core modules

#### Version 0.4 (27 Mar 2012)

* replication

#### Version 0.3 (6 Feb 2012)

* in-memory storage
* new native API
* bug fixes

#### Version 0.2 (12 Jan 2012)

* new SQS functions support
* testing with Amazon Java SDK
* bug fixes

#### Version 0.1 (12 Oct 2011)

* initial release
* DB storage
* SQS interface support
