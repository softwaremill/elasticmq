ElasticMQ
=========

tl;dr
-----

* message queue system
* emphasis on not loosing any messages
* runs stand-alone ([download](https://github.com/downloads/adamw/elasticmq/elasticmq-0.5.tar.gz)) or embedded
* Amazon SQS-compatible interface
* in-memory with optional journalling and db-backed message storage
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
volatile but fast message queue. Operations on in-memory storage can be journaled on disk, providing message durability
across server restarts/crashes. Alternatively, messages can be persisted in a database (MySQL, Postgres, H2, ...).

ElasticMQ supports data replication across a cluster, thus providing a replicated/guaranteed message queue.
Each node in the cluster can use any storage implementation.

As ElasticMQ implements a subset of the [SQS](http://aws.amazon.com/sqs/) REST interface, it is a great SQS alternative
both for testing purposes (ElasticMQ is easily embeddable) and for creating systems which work both within and
outside of the Amazon infrastructure.

The future will most probably bring even more exciting features :).

Community
---------

* [Blog](http://www.warski.org/blog/category/elasticmq/)
* Forum (discussions, help): [Google group](https://groups.google.com/forum/?fromgroups#!forum/elasticmq).

Installation: stand-alone
-------------------------

You can download the stand-alone distribution here:
* [https://github.com/.../elasticmq-0.5.tar.gz](https://github.com/downloads/adamw/elasticmq/elasticmq-0.5.tar.gz)
* [https://github.com/.../elasticmq-0.5.zip](https://github.com/downloads/adamw/elasticmq/elasticmq-0.5.zip)

Java 6 or above is required for running the server.

Installation is as easy as unpacking the `.zip`/`.tar.gz` file. The contents of the package are:
* `bin`: scripts to start the server
* `conf`: ElasticMQ and logging (logback) configuration
* `lib`: binaries
* `README.md`, `LICENSE.txt`, `NOTICE.txt`: this file, license documentation

Additionally two directories will be created when the server is started:
* `data`: stores the command journal (messages file log), if enabled
* `log`: default location for log files

You can configure ElasticMQ through the `conf/Default.scala` file. There you can choose which storage to use, should
journalling and replication be enabled, should the server expose an SQS interface, on what interface and port to bind
etc. More documentation can be found in the file itself.

Starting an embedded ElasticMQ server with an SQS interface
-----------------------------------------------------------

    // First we need to create a Node
    val node = NodeBuilder.withStorage(new InMemoryStorage)
    // Then we can expose the native client using the SQS REST interface
    val server = new SQSRestServerBuilder(node.nativeClient, 9324, new NodeAddress()).start()
    // ... use ...
    // Finally we need to stop the server and the node
    server.stop()
    node.shutdown()

Alternatively, you can use e.g. MySQL to store the data:

    val node = NodeBuilder.withStorage(new SquerylStorage(DBConfiguration.mysql("elasticmq", "root", "")))

Adding journaling to an in-memory storage
-----------------------------------------

This is as simple as wrapping the original storage (it only makes sense to wrap an in memory storage, as a DB storage
has its own persistence):

    val wrappedStorage = new FileLogConfigurator(
        inMemoryStorage,
        FileLogConfiguration(new File("/store/here"), 100000)
        .start()

Note that even though messages are now durable (restarting the server won't cause message loss), the overall capacity
of the queues (how many messages the queue can store at a time) is limited by the amount of RAM allocated to the
process.

Writing the journal is an asynchronous process, done by a separate thread. In case of a server crash, some
commands may thus be lost. Even if writing the journal was a synchronous process, data could end up not being flushed
from buffers; even then, if some OS caches are not disabled, data could be lost. That's why if you require even more
data durability, use replication.

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

Using the Amazon Java SDK to access an ElasticMQ Server
-------------------------------------------------------

To use [Amazon Java SDK](http://aws.amazon.com/sdkforjava/) as an interface to an ElasticMQ server you just need
to change the endpoint:

    client = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
    client.setEndpoint("http://localhost:9324")

The endpoint value should be the same address as the `NodeAddress` provided as an argument to
`SQSRestServerBuilder` or in the configuration file.

The `rest-sqs-testing-amazon-java-sdk` module contains some more usage examples.

Deployment scenarios
--------------------

1. In-memory storage, single node, embedded: ideal for testing
2. In-memory storage, single node, journalling: fast persistent messaging with queues bounded by the amount of memory
3. DB storage, local DB, single node: persistent messaging with unbounded queues
4. DB storage, shared DB, multiple nodes: persistent messaging. Multiple nodes can use the same database.
   The database can be replicated/backed up for data safety.
5. In-memory storage, multiple nodes, journalled, replicated: Data safety through replication and journalling.
   Fast guaranteed messaging.
6. DB storage, local DB, multiple nodes, replication: each node stores the data in a separate DB. Recommended if a
   shared DB is not available. Provides good data safety and unbounded queues.

ElasticMQ dependencies in SBT
-----------------------------

    // Includes the in-memory storage
    val elasticmqCore       = "org.elasticmq" %% "elasticmq-core"             % "0.5"

    // If you want to use the database storage
    val elasticmqStorageDb  = "org.elasticmq" %% "elasticmq-storage-database" % "0.5"

    // If you want to expose an SQS interface
    val elasticmqSqs        = "org.elasticmq" %% "elasticmq-rest-sqs"         % "0.5"

    // If you want to use replication
    val elasticmqRepl       = "org.elasticmq" %% "elasticmq-replication"      % "0.5"

    val smlResolverReleases  = "SotwareMill Public Releases"  at "http://tools.softwaremill.pl/nexus/content/repositories/releases"
    val smlResolverSnapshots = "SotwareMill Public Snapshots" at "http://tools.softwaremill.pl/nexus/content/repositories/snapshots"

ElasticMQ dependencies in Maven
-------------------------------

Dependencies:

    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-core_2.9.1</artifactId>
        <version>0.5</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-storage-database_2.9.1</artifactId>
        <version>0.5</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-rest-sqs_2.9.1</artifactId>
        <version>0.5</version>
    </dependency>
    <dependency>
        <groupId>org.elasticmq</groupId>
        <artifactId>elasticmq-replication_2.9.1</artifactId>
        <version>0.5</version>
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

*Stable*: 0.5

*Development*: 0.6-SNAPSHOT

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

Tests done on a 2009 MBP, 2.4GHz Core2Duo, 8GB RAM, no replication. Throughput is in messages per second (messages are
small).

Directly accessing the client:

    Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 1.
    Overall in-memory throughput: 41373.603641

    Running test for [in-memory], iterations: 10, msgs in iteration: 100000, thread count: 2.
    Overall in-memory throughput: 32646.665143

    Running test for [in-memory], iterations: 3, msgs in iteration: 1000000, thread count: 1.
    Overall in-memory throughput: 35157.211330

    Running test for [file log + in-memory], iterations: 10, msgs in iteration: 100000, thread count: 1.
    Overall file log + in-memory throughput: 15464.316091

    Running test for [h2], iterations: 10, msgs in iteration: 1000, thread count: 8.
    Overall h2 throughput: 334.085025

    Running test for [mysql], iterations: 10, msgs in iteration: 1000, thread count: 2.
    Overall mysql throughput: 143.620383

Through the SQS REST interface:

    Running test for [rest-sqs + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 20.
    Overall rest-sqs + in-memory throughput: 781.460628

    Running test for [rest-sqs + file log + in-memory], iterations: 10, msgs in iteration: 1000, thread count: 20.
    Overall rest-sqs + in-memory throughput: 675.851488

Note that both the client and the server were on the same machine.

Test class: `org.elasticmq.performance.LocalPerformanceTest`.

Technology
----------

* Core: [Scala](http://scala-lang.org)
* Database access: [Squeryl](http://squeryl.org/)
* Rest server: [Netty](http://www.jboss.org/netty), a high-performance,
  asynchronous, event-driven Java NIO framework.
* Replication: [JGroups](http://www.jgroups.org/)
* Testing the SQS interface: [Amazon Java SDK](http://aws.amazon.com/sdkforjava/);
  see the `rest-sqs-testing-amazon-java-sdk` module for the testsuite.
* Server configuration: [Ostrich](https://github.com/twitter/ostrich/)

Change log
----------

#### Version 0.6 (pending)

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