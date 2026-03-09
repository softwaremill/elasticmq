FROM eclipse-temurin:11-jre-noble

RUN groupadd --system elasticmq && useradd --system --gid elasticmq elasticmq

WORKDIR /opt/elasticmq

COPY server/target/elasticmq-server.jar elasticmq-server.jar
COPY server/docker/elasticmq.conf /opt/elasticmq.conf

EXPOSE 9324
VOLUME /data

USER elasticmq

ENTRYPOINT ["java", "-Dconfig.file=/opt/elasticmq.conf", "-jar", "/opt/elasticmq/elasticmq-server.jar"]
