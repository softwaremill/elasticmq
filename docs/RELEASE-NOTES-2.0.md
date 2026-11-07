# ElasticMQ 2.0 release notes

> **Draft.** 2.0 is in progress. This file covers the changes merged since 1.7.1 plus the open 2.0 work in
> [#1275](https://github.com/softwaremill/elasticmq/pull/1275). Update it as items from [`BACKLOG.md`](BACKLOG.md) land.

ElasticMQ 2.0 is a major release. It raises the platform baseline to Java 17 and Scala 2.13/3, and reworks the
embedding API so it no longer exposes Pekko internals. If you run ElasticMQ as a stand-alone server or Docker image
with the default configuration, you should not need to change anything. If you embed ElasticMQ in your application,
read [Breaking changes](#breaking-changes) first.

## Requirements

- **Java 17 or newer** (1.x ran on Java 8).
- **Scala 3 or 2.13.** Scala 2.12 artifacts are no longer published. Scala 3 is now the default build.

## Breaking changes

### Embedding API (`elasticmq-rest-sqs`)

#### The builder is configured only through `withX` methods

`TheSQSRestServerBuilder` is no longer a case class. Its constructor, fields and `copy` are private. Start from
`SQSRestServerBuilder` and chain the `withX` methods:

```scala
// 1.x
val server = TheSQSRestServerBuilder(None, None, "", 9324, NodeAddress(), true, StrictSQSLimits, "elasticmq",
  "000000000000", None).start()

// 2.0
val server = SQSRestServerBuilder.withPort(9324).withSQSLimits(StrictSQSLimits).start()
```

This lets future 2.x releases add builder options without breaking binary compatibility.

#### `withQueueEventListener` takes a `QueueEventListener` instead of an `ActorRef`

In 1.x the listener was a Pekko actor that had to understand ElasticMQ's internal `QueueEvent` messages and reply to
some of them. In 2.0 it is a plain trait with optional callbacks, usable from Scala and Java:

```scala
// 2.0
SQSRestServerBuilder
  .withQueueEventListener(new QueueEventListener {
    override def queueCreated(queue: QueueData): Unit = ???
    override def messageAdded(queueName: String, message: MessageData): Unit = ???
  })
  .start()
```

Available callbacks: `queueCreated`, `queueDeleted`, `queueMetadataUpdated`, `messageAdded`, `messageUpdated`,
`messageRemoved`. They are called one at a time, in the order the changes happen. The message callbacks finish
before the SQS response is sent, so keep them fast; the server waits for them for up to 5 seconds. An exception thrown
by a callback is logged and doesn't fail the request.

#### `withQueueManagerActor` is no longer public

The builder always creates its own queue manager. Wiring a file or SQL persistence actor into an embedded server is no
longer supported. To use persistence, run the stand-alone server (`elasticmq-server`) or open an issue describing
your use case.

#### `SQSRestServer` no longer exposes Pekko HTTP types

- `waitUntilStarted()` returns an `SQSRestServerBinding` instead of Pekko's `Http.ServerBinding`. The bound address
  is still available as `localAddress` (a `java.net.InetSocketAddress`):

  ```scala
  val port = SQSRestServerBuilder.withDynamicPort().start().waitUntilStarted().localAddress.getPort
  ```

- `SQSRestServer` is no longer a case class and can't be constructed directly.
- `stopAndGetFuture()` returns `Future[Unit]` instead of `Future[Any]`.

#### Still supported: your own actor system

`withActorSystem(ActorSystem)` is unchanged. The server runs its actors and HTTP layer on the given system and leaves
stopping it to you.

### `ElasticMQServer` moved to `elasticmq-server`

`ElasticMQServer` and `ElasticMQServerConfig` (package `org.elasticmq.server`) moved from `elasticmq-rest-sqs` to
`elasticmq-server`, together with the server's default configuration (`reference.conf`). Package names are unchanged.
If you start a configuration-driven server from code, change the dependency:

```scala
libraryDependencies += "org.elasticmq" %% "elasticmq-server" % "2.0.0"
```

As a result, `elasticmq-rest-sqs` no longer depends on `elasticmq-persistence-file` and `elasticmq-persistence-sql`, so
embedding it no longer pulls in H2, ScalikeJDBC and PureConfig.

### Native image

The native image is built with GraalVM for JDK 25 and is now a mostly static binary running on a distroless base
image (`gcr.io/distroless/base-nossl-debian13`) instead of Alpine. The image no longer contains a shell or `tini`, so
derived images that run shell commands need to change. This also fixes DNS resolution, such as binding to a Docker
network alias.

## Bug fixes

- `ReceiveMessage` matches requested message attribute names exactly or by prefix (`prefix.*`), as SQS does
  (#1268).
- Queue events now carry a snapshot of the message. Persistence could fail with `ConcurrentModificationException`
  while the queue kept updating a message (#1270).

## UI

- Works with real AWS accounts: when `SQS_ENDPOINT` is unset, the SDK uses the default AWS endpoint.
- New `NEXT_PUBLIC_AUTO_REFRESH_DISABLED` flag turns off the 1-second polling, which is recommended against real AWS
  to avoid API costs.
- Runs on Node.js 26 LTS.

## Dependencies

- Scala 3.9 LTS, Pekko 1.7.1, Pekko HTTP 1.4.2, H2 2.5.252, Logback 1.6.3.
- Build moved to sbt 2.
