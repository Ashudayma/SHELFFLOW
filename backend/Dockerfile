# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so they cache across source-only changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the application. Tests need a live Postgres, which is not available at image
# build time, so they are skipped here and run separately (see README / CI).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE only ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S spring && adduser -S spring -G spring

# busybox wget (present on Alpine) is used by the compose healthcheck.
COPY --from=build /app/target/*.jar app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
