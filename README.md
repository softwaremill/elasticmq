ElasticMQ
=========

ElasticMQ is a simple message queue system, written entirely in [Scala](http://scala-lang.org).

Currently messages are stored either in-memory, or persisted in a database (MySQL, Postgres, H2, ...)
using [Squeryl](http://squeryl.org/).

ElasticMQ implements a subset of the [SQS](http://aws.amazon.com/sqs/) REST interface,
providing an SQS alternative e.g. for testing purposes.

The REST server is implemented using [Netty](http://www.jboss.org/netty), a high-performance,
asynchronous, event-driven server Java framework.

The SQS interface has been tested using the [Amazon Java SDK](http://aws.amazon.com/sdkforjava/) library;
see the `rest-sqs-testing-amazon-java-sdk` module for the testsuite.

In the future... ElasticMQ may provide many more exciting features :).

Community
---------

Forum (discussions, help): [Google group](https://groups.google.com/forum/?fromgroups#!forum/elasticmq).

Starting an ElasticMQ server
----------------------------

    // First we need to create a Node
    val node = NodeBuilder.withInMemoryStorage().build()
    // Then we can expose the native client using the SQS REST interface
    val server = SQSRestServerFactory.start(node.nativeClient, 8888, "http://localhost:8888")
    // ... use ...
    // Finally we need to stop the server and the node
    server.stop()
    node.shutdown()

Alternatively, you can use MySQL to store the datea:

    val node = NodeBuilder.withMySQLStorage("elasticmq", "root", "").build()

ElasticMQ dependencies in SBT
-----------------------------

    val elasticmqCore = "org.elasticmq" %% "core" % "0.2"
    val elasticmqSqs  = "org.elasticmq" %% "rest-sqs" % "0.2"

    val smlResolver = "SotwareMill Public Releases" at "http://tools.softwaremill.pl/nexus/content/repositories/snapshots"

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>core_2.9.1</artifactId>
        <version>0.2</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>rest-sqs_2.9.1</artifactId>
        <version>0.2</version>
    </dependency>

And our repository:

    <repository>
        <id>SotwareMillPublicReleases</id>
        <name>SotwareMill Public Releases</name>
        <url>http://tools.softwaremill.pl/nexus/content/repositories/snapshots/</url>
    </repository>

Current versions
--------

*Stable*: 0.2

*Development*: 0.3-SNAPSHOT

DB Schema
---------

The MySQL Schema can be found in `core/src/main/resource/schema-mysql.sql` file. It should be easily adaptable to
other databases.

Logging
-------

ElasticMQ uses [Slf4j](http://www.slf4j.org/) for logging. By default no logger backend is included as a dependency,
however I recommend [Logback](http://logback.qos.ch/).

Performance
-----------

Tests done on a 2.4GHz Core2Duo, 8GB Ram:

    Storage: InMemory, number of threads: 5, number of messages: 50000
    Send took: 3 (3140), ops: 250000, ops per second: 83333
    Receive took: 5 (5057), ops: 250000, ops per second: 50000

    Storage: MySQL, number of threads: 5, number of messages: 2000
    Send took: 5 (5500), ops: 10000, ops per second: 2000
    Receive took: 74 (74269), ops: 10000, ops per second: 135

    Storage: H2, number of threads: 5, number of messages: 2000
    Send took: 0 (841), ops: 10000, ops per second: 10000
    Receive took: 30 (30388), ops: 10000, ops per second: 333

Test class: `org.elasticmq.performance.MultiThreadPerformanceTest`.

Change log
----------

#### Version 0.3

* in-memory storage
* new native API
* bug fixes

#### Version 0.2

* new SQS functions support
* testing with Amazon Java SDK
* bug fixes

#### Version 0.1

* initial release
* DB storage
* SQS interface support