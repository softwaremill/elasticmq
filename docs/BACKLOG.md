# Backlog

Candidate changes for the next major release (2.0) and later 2.x minors.

Constraints: the JVM baseline stays at **JDK 17** and **Scala 2.13** cross-building is kept.

## Compatibility-breaking changes

These need a major version. Ship them together in 2.0.

### Embedding API (`elasticmq-rest-sqs`)

- [x] **Make `TheSQSRestServerBuilder`'s constructor private.** It is a public case class with an 11-argument positional constructor, so every new option breaks binary compatibility. Keep only the `withX` methods and the `SQSRestServerBuilder` entry point.
- [x] **Hide Pekko types behind a small facade.** `withActorSystem(ActorSystem)`, `withQueueManagerActor(ActorRef)` and `withQueueEventListener(ActorRef)` expose Pekko classic types. A typed facade (e.g. a `QueueEventListener` trait) would let 2.x upgrade Pekko without breaking embedders.
- [x] **Move `ElasticMQServer` and `ElasticMQServerConfig` into the `server` module** (or a new `elasticmq-server-core` artifact). They are in package `org.elasticmq.server` but live in `rest-sqs`.

### Configuration

- [x] **Normalise config key style.** Top-level keys are kebab-case (`bind-port`, `node-address`) but `aws.accountId` and queue definitions (`defaultVisibilityTimeout`, `deadLettersQueue`, `copyTo`, `moveTo`) are camelCase. Pick one style; read the old keys with a deprecation warning for one release. The file-persistence writer (`queues-storage`) must write the new keys and still read the old format.
- [x] **Build queue URLs from the incoming request by default.** The wildcard host (`node-address.host = "*"`) already does this. Making it the default fixes the common Docker/CI case where clients reach the server under a service alias (see #1212). Changes returned queue URLs for users relying on the `localhost` default.

### SQS behaviour

- [x] **Enforce `MessageRetentionPeriod`.** Currently ignored, so messages never expire. SQS defaults to 4 days. Decided: the default follows `sqs-limits` (strict: 4 days, relaxed: never expire); an explicit value is always enforced.
- [x] **Enforce per-queue `MaximumMessageSize`.** Currently ignored; strict limits always allow 1 MiB. Now counts body and attributes in bytes, as SQS does.

## Minor changes

Additive or internal; can land in 2.0 or any 2.x minor.

### SQS feature parity

- [ ] Accept, store and return `KmsMasterKeyId`, `KmsDataKeyReusePeriodSeconds` and `SqsManagedSseEnabled` instead of failing with `InvalidAttributeName` (#130). No actual encryption.
- [ ] Accept and store `RedriveAllowPolicy`; optionally enforce it when assigning a DLQ.
- [ ] Accept and store `DeduplicationScope` and `FifoThroughputLimit`.
- [ ] Store and return `Policy` verbatim (currently ignored).
- [ ] General policy: accept every documented SQS queue attribute and log a warning only when it has no effect.
- [ ] Fair queues: confirm nothing beyond `MessageGroupId` on standard queues is needed and close #1139.
- [ ] Optional in-flight message limit returning `OverLimit` (default 120,000) (#160).
- [ ] Optional failure injection: configurable error rates or targeted errors per action (#804).

### Configuration & docs

- [ ] Move the Pekko settings from `rest-sqs/src/main/resources/application.conf` into `reference.conf`, so custom config files no longer need `include classpath("application.conf")` (existing includes keep working).
- [x] Fix the `SQSRestServerBuilder` Scaladoc: it says the default `sqsLimits` is relaxed, but the code uses `StrictSQSLimits`.
- [ ] Fix the "Amazon SQS allows 256KB message payloads" comment in `application.conf`; strict limits already allow 1 MiB. Check `max-uri-length` accordingly.
- [ ] Restate the Docker image contract in the README: entrypoint, config path (`/opt/elasticmq.conf`), data volume (`/data`).
- [ ] Update the README version automatically in the release flow (#1026).

### Internals

- [ ] Extract a `QueueManagerActor` `*Ops` class like `QueueActor` (TODO in `QueueManagerActor.scala`).
- [ ] Log at startup which SQL database is used and how many queues/messages were restored.

### Docker & native image

- [ ] Also publish images to GHCR to avoid Docker Hub rate limits (#938).
- [ ] Consider a smaller JVM image (distroless or `jlink`-trimmed JRE 17).
- [ ] Re-test the native image for the memory leak in #590 and close it if it no longer reproduces.

### UI

- [ ] Purge queue (`PurgeQueue`).
- [ ] DLQ redrive: start, cancel and list message move tasks.
- [ ] Edit queue attributes and redrive policy (`SetQueueAttributes`).
- [ ] Manage tags (`TagQueue`, `UntagQueue`, `ListQueueTags`).
- [ ] Change visibility of a received message (`ChangeMessageVisibility`).
- [ ] Read-only mode flag, and a docs warning that the UI has no authentication when pointed at real AWS with write credentials.
- [ ] Tie the UI version to the server release (`ui/package.json` is `0.1.0`).

### Build, CI & testing

- [ ] Replace `actions/checkout@v2` (9 uses) and `actions/setup-node@v3` in `ci.yml`; pin by SHA.
- [ ] Replace the archived `actions/upload-release-asset@v1` with `gh release upload` or `softprops/action-gh-release`.
- [ ] Add a CI run on a newer LTS JDK (21 or 25) alongside 17.
- [ ] Remove AWS SDK v1 (end of support) from `rest-sqs-testing-amazon-java-sdk` and `performance-tests`, together with the `--add-opens=java.base/java.lang` test flag; bump SDK v2 from 2.25.60.
- [ ] Replace `aws-java-sdk-s3` 1.11.601 in `project/plugins.sbt` with SDK v2, or drop the S3 jar upload in favour of the GitHub release asset.
- [ ] Add MiMa binary-compatibility checks after 2.0.0 ships.
