FROM alpine:3.10.0
# tini is needed so that SIGTERM works properly
# see: https://hynek.me/articles/docker-signals/ and PID1 problems
RUN apk add --no-cache tini
COPY elasticmq-native-server /opt/docker/elasticmq-native-server
#RUN chmod +x /opt/docker/elasticmq-native-server
COPY ../../../../server/docker/elasticmq.conf /opt/
EXPOSE 9324/tcp
ENTRYPOINT [ "/sbin/tini", "--", "/opt/docker/elasticmq-native-server", "-Dconfig.file=/opt/elasticmq.conf"]
