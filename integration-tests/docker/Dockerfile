FROM ghcr.io/graalvm/jdk-community:17

RUN microdnf update -y \
    && microdnf install -y procps-ng \
    && microdnf clean all

RUN mkdir /elasticmq
WORKDIR /elasticmq

ADD server/target/scala-2.13/elasticmq-server-assembly-*.jar .

RUN ln -s elasticmq-server-assembly-*.jar elasticmq-server.jar
RUN mkdir /elasticmq/data

ADD examples/logback.xml .

ENTRYPOINT java -agentlib:native-image-agent=config-merge-dir=/elasticmq/native-image \
    -Dconfig.file=/elasticmq/conf/elasticmq.conf \
    -Dlogback.configurationFile=/elasticmq/logback.xml \
    -jar /elasticmq/elasticmq-server.jar
