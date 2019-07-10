FROM alpine:3.10.0
# tini is needed so that SIGTERM works properly
# see: https://hynek.me/articles/docker-signals/ and PID1 problems
RUN apk add --no-cache tini
COPY out /opt/docker/out
RUN chmod +x /opt/docker/out
EXPOSE 9324/tcp
ENTRYPOINT [ "/sbin/tini", "--", "/opt/docker/out" ]