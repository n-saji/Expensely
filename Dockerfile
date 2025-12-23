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

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/expensely_backend-1.0.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
