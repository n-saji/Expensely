# Stage 1: Build the JAR
FROM gradle:8.14-jdk17 AS builder

WORKDIR /app

# Copy the entire project
COPY . .

# Grant execute permission for gradlew
RUN chmod +x ./gradlew

# Build the JAR (skip tests if needed)
RUN ./gradlew clean build -x test

# Stage 2: Create minimal runtime image
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/expensely_backend-1.0.0.jar app.jar

# Expose port
EXPOSE 8080

# Health check to monitor container health
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/ping || exit 1

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
