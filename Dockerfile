FROM java:8

ARG VERSION=0.13.8
ARG VERSION_SHASUM=41525fa141f36145e4bb54211bee0906a1901d1cfcbb0d86b8f4367b521e69e1

COPY elasticmq.conf /elasticmq.conf

RUN curl -SsL --retry 5 -O /elasticmq-server.jar --output /elasticmq-server.jar "https://s3-eu-west-1.amazonaws.com/softwaremill-public/elasticmq-server-${VERSION}.jar" && \
    shasum -a 256 elasticmq-server.jar | grep "${VERSION_SHASUM}"

ENTRYPOINT ["/usr/bin/java", "-Dconfig.file=elasticmq.conf", "-jar", "/elasticmq-server.jar"]

EXPOSE 9324