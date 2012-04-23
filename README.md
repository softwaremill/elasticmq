ElasticMQ
=========

tl;dr
-----

* message queue system
* emphasis on not loosing any messages
* Amazon SQS-compatible interface
* in-memory and db-backed message storage
* optionally replicated (guaranteed messaging)

Summary
-------

ElasticMQ is a message queue system, offerring Java, Scala and an [SQS](http://aws.amazon.com/sqs/)-compatible
REST interface.

ElasticMQ follows the semantics of SQS. Messages are received by polling the queue.
When a message is received, it is blocked for a specified amount of time (the visibility timeout).
If the message isn't deleted during that time, it will be again available for delivery.
Moreover, queues and messages can be configured to always deliver messages with a delay.

The focus in ElasticMQ is to make sure that the messages are delivered, and that no message is lost.
It may happen, however, that a message is delivered twice (if, for example, a client dies after receiving a message and
processing it, but before deleting). That's why clients of ElasticMQ (and Amazon SQS) should be idempotent.

There are several message storage implementations. Messages can be stored entirely in-memory, providing a
volatile but fast message queue. Alternatively, they can be persisted in a database (MySQL, Postgres, H2, ...),
making the data durable.

ElasticMQ supports data replication across a cluster, thus providing a replicated/guaranteed message queue.
Each node can use any storage implementation.

As ElasticMQ implements a subset of the [SQS](http://aws.amazon.com/sqs/) REST interface, it is a great SQS alternative
both for testing purposes (ElasticMQ is easily embeddable) and for creating systems which work both within and
outside of the Amazon infrastructure.

The future will most probably bring more exciting features :).

Community
---------

* [Blog](http://www.warski.org/blog/category/elasticmq/)
* Forum (discussions, help): [Google group](https://groups.google.com/forum/?fromgroups#!forum/elasticmq).

Starting an ElasticMQ server with an SQS interface
--------------------------------------------------

    // First we need to create a Node
    val node = NodeBuilder.withStorage(new InMemoryStorage)
    // Then we can expose the native client using the SQS REST interface
    val server = SQSRestServerFactory.start(node.nativeClient, 9324, new NodeAddress("http://localhost:9324"))
    // ... use ...
    // Finally we need to stop the server and the node
    server.stop()
    node.shutdown()

Alternatively, you can use MySQL to store the data:

    val node = NodeBuilder.withStorage(new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "")))

Starting a replicated storage
-----------------------------

Any storage can be replicated by wrapping it using `ReplicatedStorageConfigurator`. Nodes can join and leave the cluster
at any time; existing data will be transferred to new cluster members.

Storage commands can be replicated in several modes:

* fire-and-forget (`DoNotWaitReplicationMode`)
* waiting for at least one cluster member to apply the changes (`WaitForAnyReplicationMode`)
* waiting for a majority of cluster members (`WaitForMajorityReplicationMode`)
* waiting for all (`WaitForAllReplicationMode`).

Client operations return only when the specified number of members applied the changes.

Example:

    val storage = new InMemoryStorage
    val replicatedStorage = ReplicatedStorageConfigurator.start(storage, NodeAddress(),
            WaitForMajorityReplicationMode)
    val node = NodeBuilder.withStorage(replicatedStorage)

    // ... use ...

    node.shutdown()
    storage.shutdown()

The provided `NodeAddress`es are entirely logical (the actual value can be any string) and for example can be used
by ElasticMQ clients to determine which node is the master.

Operations can be only executed on the master node. If you attempt to execute an operation on a node which is not
the master, the `NodeIsNotMasterException` exception will be thrown, containing the master node address, if available.

In case of cluster partitions, replication is designed to only operate on the parition which contains
a majority of nodes (`n/2+1`). Otherwise data could get easily corrupted, if two disconnected cluster partitions
(split-brain) changed the same things; such a situation could lead to a very high number of duplicate deliveries and an
unmergeable state.

That is also why when creating the replicated storage, you must provide the expected number of nodes. Note that
an even number of nodes makes most sense (e.g. in a 3-node cluster, 2 nodes must be active in order for the cluster
to work).

Deployment scenarios
--------------------

1. In-memory storage, single node: ideal for testing
2. DB storage, local DB, single node: persistent messaging
3. DB storage, shared DB, multiple nodes: persistent messaging. Multiple nodes can use the same database.
   The database can be replicated/backed up for data safety.
4. In-memory storage, multiple nodes, replicated: good for systems where at least one node is always alive.
   Can provide data safety if using the right replication mode.
5. DB storage, local DB, multiple nodes, replication: each node stores the data in a separate DB. Recommended if a
   shared DB is not available. Provides good data safety.

ElasticMQ dependencies in SBT
-----------------------------

    // Includes the in-memory storage
    val elasticmqCore       = "org.elasticmq" %% "core"             % "0.4"

    // If you want to use the database storage
    val elasticmqStorageDb  = "org.elasticmq" %% "storage-database" % "0.4"

    // If you want to expose an SQS interface
    val elasticmqSqs        = "org.elasticmq" %% "rest-sqs"         % "0.4"

    // If you want to use replication
    val elasticmqRepl       = "org.elasticmq" %% "replication"      % "0.4"

    val smlResolverReleases  = "SotwareMill Public Releases"  at "http://tools.softwaremill.pl/nexus/content/repositories/releases"
    val smlResolverSnapshots = "SotwareMill Public Snapshots" at "http://tools.softwaremill.pl/nexus/content/repositories/snapshots"

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>core_2.9.1</artifactId>
        <version>0.4</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>storage-database_2.9.1</artifactId>
        <version>0.4</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>rest-sqs_2.9.1</artifactId>
        <version>0.4</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>replication_2.9.1</artifactId>
        <version>0.4</version>
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

*Stable*: 0.4

*Development*: 0.5-SNAPSHOT

DB Schema
---------

The MySQL Schema can be found in `storage-database/src/main/resource/schema-mysql.sql` file. It should be easily
adaptable to other databases.

Logging
-------

ElasticMQ uses [Slf4j](http://www.slf4j.org/) for logging. By default no logger backend is included as a dependency,
however [Logback](http://logback.qos.ch/) is recommended.

Performance
-----------

Tests done on a 2.4GHz Core2Duo, 8GB RAM, no replication:

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

Technology
----------

* Core: [Scala](http://scala-lang.org)
* Database access: [Squeryl](http://squeryl.org/)
* Rest server: [Netty](http://www.jboss.org/netty), a high-performance,
  asynchronous, event-driven Java NIO framework.
* Replication: [JGroups](http://www.jgroups.org/)
* Testing the SQS interface: [Amazon Java SDK](http://aws.amazon.com/sdkforjava/);
  see the `rest-sqs-testing-amazon-java-sdk` module for the testsuite.

Change log
----------

#### Version 0.5 (pending)

* file log for message storage
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