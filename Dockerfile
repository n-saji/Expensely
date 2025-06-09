# Use a lightweight Java image
FROM openjdk:17-jdk-slim

# Set working directory inside container
WORKDIR /app

# Copy built jar into container
COPY expensely_backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port (match the port your app runs on, typically 8080)
EXPOSE 8080

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"]
