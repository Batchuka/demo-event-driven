# syntax=docker/dockerfile:1

# Compiles all modules once; the four images below reuse this stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# Runtime image: docker build --build-arg MODULE=<orchestrator|finance|customs-clearance|logistics>
FROM eclipse-temurin:21-jre AS runtime
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}.jar app.jar
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
