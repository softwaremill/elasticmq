# Stage 1: Compile a mostly static native binary using GraalVM Community for JDK 25 (multi-arch: amd64, arm64).
# The binary links everything statically except glibc (--static-nolibc), so it resolves hostnames via DNS and
# runs on any image with glibc >= the builder's (Oracle Linux 9: glibc 2.34).
FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol9 AS builder

WORKDIR /build

COPY server/target/elasticmq-server.jar elasticmq.jar
COPY native-server/target/elasticmq-native-server.jar elasticmq-native.jar

RUN PAGE_SIZE_ARG=$(uname -m | grep -Eiq 'arm|aarch64' && echo '-H:PageSize=64K' || echo '') && \
    native-image \
    -cp elasticmq.jar:elasticmq-native.jar \
    -H:+UnlockExperimentalVMOptions \
    '-H:IncludeResources=.*conf' \
    '-H:IncludeResources=version' \
    '-H:IncludeResources=.*\.properties' \
    ${PAGE_SIZE_ARG} \
    -H:-UnlockExperimentalVMOptions \
    -H:+ReportExceptionStackTraces \
    -o elasticmq-native-server \
    '--initialize-at-build-time=scala.Symbol$' \
    --static-nolibc \
    org.elasticmq.server.Main

# Stage 2: Minimal distroless runtime providing glibc only (Java's TLS doesn't need OpenSSL)
FROM gcr.io/distroless/base-nossl-debian13

WORKDIR /opt/elasticmq

COPY --from=builder /build/elasticmq-native-server /opt/elasticmq/bin/elasticmq-native-server
COPY server/docker/elasticmq.conf /opt/elasticmq.conf
COPY server/src/main/resources/logback.xml /opt/logback.xml

EXPOSE 9324
VOLUME /data

# No init process (distroless has no shell or tini): native executables install signal handlers by default, so
# SIGTERM/SIGINT trigger a graceful shutdown. Use `docker run --init` if an init process is needed to reap zombies.
ENTRYPOINT ["/opt/elasticmq/bin/elasticmq-native-server", \
    "-Dconfig.file=/opt/elasticmq.conf", \
    "-Dlogback.configurationFile=/opt/logback.xml"]
