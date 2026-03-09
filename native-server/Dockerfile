# Stage 1: Compile native binary using GraalVM
FROM ghcr.io/graalvm/graalvm-ce:ol8-java11-22.1.0 AS builder

WORKDIR /build

RUN gu install native-image && \
    ln -s /opt/graalvm-ce-22.1.0*/bin/native-image /usr/local/bin/native-image

COPY server/target/elasticmq-server.jar elasticmq.jar
COPY native-server/target/elasticmq-native-server.jar elasticmq-native.jar

RUN PAGE_SIZE_ARG=$(uname -m | grep -Eiq 'arm|aarch64' && echo '-H:PageSize=64K' || echo '') && \
    /usr/local/bin/native-image \
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
    --report-unsupported-elements-at-runtime \
    '--initialize-at-build-time=scala.Symbol$' \
    --allow-incomplete-classpath \
    --install-exit-handlers \
    --no-fallback \
    --verbose \
    --static \
    org.elasticmq.server.Main

# Stage 2: Minimal Alpine runtime
FROM alpine:3.22

RUN apk add --no-cache tini

WORKDIR /opt/elasticmq

COPY --from=builder /build/elasticmq-native-server /opt/elasticmq/bin/elasticmq-native-server
COPY server/docker/elasticmq.conf /opt/elasticmq.conf
COPY server/src/main/resources/logback.xml /opt/logback.xml

EXPOSE 9324 9325
VOLUME /data

ENTRYPOINT ["/sbin/tini", "--", "/opt/elasticmq/bin/elasticmq-native-server", \
    "-Dconfig.file=/opt/elasticmq.conf", \
    "-Dlogback.configurationFile=/opt/logback.xml"]
