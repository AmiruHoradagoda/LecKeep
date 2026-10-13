FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime-base

# Set the working directory inside the container
WORKDIR /app

# Compose sets SERVER_PORT=8080; non-Docker development still defaults to 8081.
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/leckeep.jar"]

# Compose builds from source so a fresh clone needs no host Maven/JDK or prebuilt JAR.
FROM runtime-base AS from-source
COPY --from=build /build/target/*.jar /app/leckeep.jar

# Default target preserves Jenkins' existing package-then-image workflow.
FROM runtime-base AS runtime
COPY target/*.jar /app/leckeep.jar
