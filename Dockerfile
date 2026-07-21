# syntax=docker/dockerfile:1.7

FROM clojure:temurin-21-tools-deps AS build

WORKDIR /app
COPY . .

RUN clojure -Sforce -T:build all


FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget \
 && addgroup -S -g 10001 pommes \
 && adduser -S -D -H -u 10001 -G pommes pommes

WORKDIR /pommes-connection

COPY --from=build \
  --chown=pommes:pommes \
  /app/target/pommes-connection-standalone.jar \
  ./pommes-connection-standalone.jar

USER pommes

EXPOSE 3333

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:3333/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /pommes-connection/pommes-connection-standalone.jar"]
