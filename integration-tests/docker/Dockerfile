FROM ghcr.io/graalvm/graalvm-ce:ol8-java11-22.1.0

RUN microdnf update -y \
    && microdnf install -y procps-ng \
    && microdnf clean all

RUN gu install native-image

RUN mkdir /elasticmq
WORKDIR /elasticmq

ADD server/target/scala-2.13/elasticmq-server-assembly-*.jar .

RUN ln -s elasticmq-server-assembly-*.jar elasticmq-server.jar
RUN mkdir /elasticmq/data

ADD integration-tests/docker/logback.xml .

ENTRYPOINT java -agentlib:native-image-agent=config-merge-dir=/elasticmq/native-image \
    -Dconfig.file=/elasticmq/conf/elasticmq.conf \
    -Dlogback.configurationFile=/elasticmq/logback.xml \
    -jar /elasticmq/elasticmq-server.jar
