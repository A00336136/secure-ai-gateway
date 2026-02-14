# Multi-stage Dockerfile with security best practices

# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install security updates
RUN apk update && \
    apk upgrade && \
    apk add --no-cache dumb-init && \
    rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=appuser:appgroup /app/target/secure-ai-gateway.jar app.jar

# Switch to non-root user
USER appuser:appgroup

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run application with security options
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+ExitOnOutOfMemoryError", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", \
     "app.jar"]
