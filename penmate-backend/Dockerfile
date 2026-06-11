# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jre-jammy AS runtime

ENV JAVA_OPTS="" \
    SERVER_PORT=8080

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system penmate && \
    useradd --system --gid penmate --home-dir /app --shell /usr/sbin/nologin penmate

ARG BACKEND_JAR=penmate-backend/target/penmate-backend-*.jar
COPY ${BACKEND_JAR} /app/penmate-backend.jar

USER penmate

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/penmate-backend.jar"]
