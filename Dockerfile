FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy gradle wrapper and settings
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy source code
COPY src src

# Grant execution rights to gradlew
RUN chmod +x gradlew

# Run build with stacktrace and info to verify compilation
CMD ["./gradlew", "classes", "--no-daemon", "--stacktrace", "--info"]
