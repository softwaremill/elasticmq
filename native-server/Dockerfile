# Stage 1: Compile native binary using GraalVM for JDK 17 (multi-arch)
FROM ghcr.io/graalvm/native-image-community:17 AS builder

WORKDIR /build

COPY server/target/elasticmq-server.jar elasticmq.jar
COPY native-server/target/elasticmq-native-server.jar elasticmq-native.jar

RUN PAGE_SIZE_ARG=$(uname -m | grep -Eiq 'arm|aarch64' && echo '-H:PageSize=64K' || echo '') && \
    native-image \
    -cp elasticmq.jar:elasticmq-native.jar \
    '-H:IncludeResources=.*conf' \
    '-H:IncludeResources=version' \
    '-H:IncludeResources=.*\.properties' \
    -H:+ReportExceptionStackTraces \
    -H:-ThrowUnsafeOffsetErrors \
    ${PAGE_SIZE_ARG} \
    -H:Name=elasticmq-native-server \
    --enable-http \
    --enable-https \
    --enable-url-protocols=https,http \
    '--initialize-at-build-time=scala.Symbol$' \
    --install-exit-handlers \
    --no-fallback \
    --verbose \
    org.elasticmq.server.Main

# Stage 2: glibc runtime (native-image-community:17 is not musl)
FROM debian:bookworm-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends tini && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/elasticmq

COPY --from=builder /build/elasticmq-native-server /opt/elasticmq/bin/elasticmq-native-server
COPY server/docker/elasticmq.conf /opt/elasticmq.conf
COPY server/src/main/resources/logback.xml /opt/logback.xml

EXPOSE 9324
VOLUME /data

ENTRYPOINT ["/usr/bin/tini", "--", "/opt/elasticmq/bin/elasticmq-native-server", \
    "-Dconfig.file=/opt/elasticmq.conf", \
    "-Dlogback.configurationFile=/opt/logback.xml"]
