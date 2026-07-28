# Shared build for every Spring Boot service.
# Build with:  docker build --build-arg SERVICE=payment-service -f backend/Dockerfile .

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POMs first so dependency resolution is cached independently of
# source changes - a code edit then rebuilds in seconds rather than re-downloading
# the world.
COPY backend/pom.xml ./backend/pom.xml
COPY backend/common/platform-common/pom.xml ./backend/common/platform-common/pom.xml
COPY backend/common/loan-engine/pom.xml ./backend/common/loan-engine/pom.xml
COPY backend/services ./backend/services
RUN find backend/services -mindepth 2 -maxdepth 2 ! -name pom.xml -exec rm -rf {} + || true
RUN mvn -f backend/pom.xml -B -q dependency:go-offline -DskipTests || true

COPY backend ./backend
ARG SERVICE
RUN mvn -f backend/pom.xml -B -pl services/${SERVICE} -am package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-jammy AS runtime
ARG SERVICE
ENV SERVICE=${SERVICE}

# Runs as an unprivileged user: a container escape should not start from root.
RUN groupadd --system --gid 1001 mfin \
    && useradd --system --uid 1001 --gid mfin --home /app mfin \
    && mkdir -p /app && chown -R mfin:mfin /app
WORKDIR /app

COPY --from=build --chown=mfin:mfin /workspace/backend/services/${SERVICE}/target/${SERVICE}.jar /app/app.jar

USER mfin
EXPOSE 8080

# Container-aware heap sizing, and an explicit exit on OOM so Kubernetes restarts
# the pod rather than leaving it alive but unable to serve.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.security.egd=file:/dev/./urandom \
-Duser.timezone=UTC"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O- http://localhost:${SERVER_PORT:-8080}/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
