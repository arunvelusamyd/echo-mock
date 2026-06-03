# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar (no local Maven/JDK needed) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so they cache across source-only changes.
COPY pom.xml .
RUN mvn -q -B -e dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Stage 2: slim runtime ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as an unprivileged user. UID/GID 10001 avoids colliding with the default
# uid 1000 user the Ubuntu-based temurin image already ships.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app

# The repackaged executable Spring Boot WAR (target/echo-mock-1.0.0.war).
COPY --from=build /build/target/echo-mock-*.war /app/app.war

# Honour the container memory limit when sizing the heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Config is NOT baked into the WAR. Mount application.yml and mocks.yml at
# /etc/echo-mock (e.g. a ConfigMap or a bind mount). Spring loads application.yml
# from the additional location; the mock definitions come from MOCK_CONFIG_PATH.
ENV SPRING_CONFIG_ADDITIONAL_LOCATION="optional:file:/etc/echo-mock/" \
    MOCK_CONFIG_PATH="/etc/echo-mock/mocks.yml"

EXPOSE 8080
USER app

ENTRYPOINT ["java", "-jar", "/app/app.war"]
