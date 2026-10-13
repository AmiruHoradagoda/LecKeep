# Use an official OpenJDK runtime as a base image
FROM eclipse-temurin:17-jre-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/leckeep_backend-0.0.1-SNAPSHOT.jar /app/leckeep.jar

# Expose the application port (default for Spring Boot is 8080)
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/leckeep.jar"]
