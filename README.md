ElasticMQ
=========

ElasticMQ is a message queue system, written entirely in [Scala](http://scala-lang.org).

Currently messages are stored either in-memory, or persisted in a database (MySQL, Postgres, H2, ...)
using [Squeryl](http://squeryl.org/).

Messages can be replicated across several nodes. Clustering is implemented using [JGroups](http://www.jgroups.org/).

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

Starting an ElasticMQ server with an SQS interface
--------------------------------------------------

    // First we need to create a Node
    val node = NodeBuilder.withStorage(new InMemoryStorage).build()
    // Then we can expose the native client using the SQS REST interface
    val server = SQSRestServerFactory.start(node.nativeClient, 8888, "http://localhost:8888")
    // ... use ...
    // Finally we need to stop the server and the node
    server.stop()
    node.shutdown()

Alternatively, you can use MySQL to store the datea:

    val node = NodeBuilder.withStorage(new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "")))

Starting a replicated storage
-----------------------------

Any storage can be replicated by wrapping it using `ReplicatedStorageConfigurator`. Nodes can join and leave the cluster
at any time; existing data will be transferred to new cluster member.

Storage commands commands can be replicated in several modes: fire-and-forget (`DoNotWaitReplicationMode`), waiting
for at least one cluster member to apply the changes (`WaitForAnyReplicationMode`), waiting for a majority of cluster
members (`WaitForMajorityReplicationMode`), or waiting for all (`WaitForAllReplicationMode`). Client operations return
only when the specified number of members applied the changes.

    val storage = new InMemoryStorage
    val replicatedStorage = ReplicatedStorageConfigurator.start(storage, NodeAddress(),
            WaitForMajorityReplicationMode)
    val node = NodeBuilder.withStorage(replicatedStorage)

    // ... use ...

    node.shutdown()
    storage.shutdown()

ElasticMQ dependencies in SBT
-----------------------------

    val elasticmqCore = "org.elasticmq" %% "core"           % "0.4-SNAPSHOT"
    val elasticmqSqs  = "org.elasticmq" %% "rest-sqs"       % "0.4-SNAPSHOT"
    val elasticmqRepl = "org.elasticmq" %% "replication"    % "0.4-SNAPSHOT"

    val smlResolverReleases  = "SotwareMill Public Releases"  at "http://tools.softwaremill.pl/nexus/content/repositories/releases"
    val smlResolverSnapshots = "SotwareMill Public Snapshots" at "http://tools.softwaremill.pl/nexus/content/repositories/snapshots"

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>core_2.9.1</artifactId>
        <version>0.4-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>rest-sqs_2.9.1</artifactId>
        <version>0.4-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>replication_2.9.1</artifactId>
        <version>0.4-SNAPSHOT</version>
    </dependency>

And our repositories:

    <repository>
        <id>SotwareMillPublicReleases</id>
        <name>SotwareMill Public Releases</name>
        <url>http://tools.softwaremill.pl/nexus/content/repositories/releases/</url>
    </repository>
    <repository>
        <id>SotwareMillPublicSnapshots</id>
        <name>SotwareMill Public Snapshots</name>
        <url>http://tools.softwaremill.pl/nexus/content/repositories/snapshots/</url>
    </repository>

Current versions
--------

*Stable*: 0.3

*Development*: 0.4-SNAPSHOT

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

#### Version 0.4 (pending)

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