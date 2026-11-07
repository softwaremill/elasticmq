# ElasticMQ 2.0 release notes

> **Draft.** 2.0 is in progress. This file covers the changes merged since 1.7.1. Update it as items from
> [`BACKLOG.md`](BACKLOG.md) land.

ElasticMQ 2.0 is a major release. It raises the platform baseline to Java 17 and Scala 2.13/3, reworks the
embedding API so it no longer exposes Pekko internals, renames configuration keys to a single style, and builds queue
URLs from the incoming request by default. 1.x configuration files keep working, with deprecation warnings. If you
embed ElasticMQ in your application, or your clients depend on the exact queue URLs returned, read
[Breaking changes](#breaking-changes) first.

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

#### Queue URLs are built from the incoming request by default

The builder used to return queue URLs based on the bind address (`http://localhost:<port>`). It now uses the
protocol, host and port of each request, like the stand-alone server (see
[below](#queue-urls-are-built-from-the-incoming-request-by-default-1)). For clients connecting to `localhost`, the URLs
don't change. To get the 1.x behaviour, call `withServerAddressFromBinding()`; to fix the address, call
`withServerAddress(NodeAddress(...))` as before.

### `ElasticMQServer` moved to `elasticmq-server`

`ElasticMQServer` and `ElasticMQServerConfig` (package `org.elasticmq.server`) moved from `elasticmq-rest-sqs` to
`elasticmq-server`, together with the server's default configuration (`reference.conf`). Package names are unchanged.
If you start a configuration-driven server from code, change the dependency:

```scala
libraryDependencies += "org.elasticmq" %% "elasticmq-server" % "2.0.0"
```

As a result, `elasticmq-rest-sqs` no longer depends on `elasticmq-persistence-file` and `elasticmq-persistence-sql`, so
embedding it no longer pulls in H2, ScalikeJDBC and PureConfig.

### Configuration

#### Configuration keys are kebab-case

All configuration keys now use kebab-case, like the top-level keys already did:

| 1.x                                    | 2.0                                           |
|----------------------------------------|-----------------------------------------------|
| `aws.accountId`                        | `aws.account-id`                              |
| `defaultVisibilityTimeout`             | `default-visibility-timeout`                  |
| `receiveMessageWait`                   | `receive-message-wait`                        |
| `deadLettersQueue { maxReceiveCount }` | `dead-letters-queue { max-receive-count }`    |
| `contentBasedDeduplication`            | `content-based-deduplication`                 |
| `copyTo`                               | `copy-to`                                     |
| `moveTo`                               | `move-to`                                     |

The queue keys apply to the `queues` section and to `auto-create-queues.template`. `delay`, `fifo`, `tags` and
`dead-letters-queue.name` are unchanged.

The 1.x keys are still read, with a deprecation warning naming the file and line. They will be removed in a future
major release. If both the old and the new key are set, the old one wins.

The queue storage file (`queues-storage`) is now written with the new keys. A file written by 1.x is still read on
startup, and is rewritten in the new format the next time ElasticMQ saves it. A file written by 2.0 can't be read by
1.x.

#### Queue URLs are built from the incoming request by default

`node-address.host` now defaults to `"*"`: returned queue URLs use the protocol, host and port the client connected
to, followed by `node-address.context-path`. A client reaching ElasticMQ as `http://elasticmq:9324` (a Docker Compose
service, a CI service container) now gets queue URLs it can use, instead of `http://localhost:9324/...` (#1212).
`node-address.protocol` and `node-address.port` are ignored unless you set a host.

Clients connecting to `localhost:9324` see no difference. Clients connecting any other way (another host name, an IP
address, a mapped port) get URLs with that host and port instead of `localhost:9324`. To keep the 1.x URLs, set:

```
node-address {
  host = "localhost"
}
```

### Native image

The native image is built with GraalVM for JDK 25 and is now a mostly static binary running on a distroless base
image (`gcr.io/distroless/base-nossl-debian13`) instead of Alpine. The image no longer contains a shell or `tini`, so
derived images that run shell commands need to change. This also fixes DNS resolution, such as binding to a Docker
network alias.

## Bug fixes

- `ReceiveMessage` matches requested message attribute names exactly or by prefix (`prefix.*`), as SQS does
  (#1268).
- With `node-address.host = "*"`, a request sent to a queue URL (as SDK v1 does for `ListDeadLetterSourceQueues`)
  returned queue URLs with the request path in them, e.g. `http://host:9324/000000000000/dlq/000000000000/source`.
- Queues created by `auto-create-queues` had their creation time set to 1970-01-01.
- Quoted `tags` keys containing dots (e.g. `"app.team" = "x"`) in a queue definition or the queue storage file no
  longer fail queue loading.
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
