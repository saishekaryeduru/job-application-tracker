# Multi-stage build: Maven compiles and packages the jar, the runtime image
# carries only a JRE and the artifact -- no sources, no build tooling.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Resolve dependencies first so this layer is cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI on every push; the image build stays a packaging step.
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Run as an unprivileged user; the app needs no root-owned paths.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
