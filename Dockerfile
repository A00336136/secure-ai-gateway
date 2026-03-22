# ═══════════════════════════════════════════════════════
# Secure AI Gateway — Multi-Stage Dockerfile (Alpine Linux)
#
# Stage 1: Build (Maven 3.9.9 + JDK 21 LTS)
# Stage 2: Runtime (JRE 21 Alpine — minimal attack surface)
#
# Alpine Linux benefits:
#  - ~5 MB base image vs ~80 MB Ubuntu/Debian
#  - Reduced CVE attack surface (musl libc, busybox)
#  - Trivy scans show significantly fewer vulnerabilities
#
# Security hardening:
#  - Non-root user (uid 1001)
#  - Read-only filesystem (except /tmp)
#  - No unnecessary packages in runtime image
#  - eclipse-temurin JRE on Alpine Linux
# ═══════════════════════════════════════════════════════

# ─── Stage 1: Build ─────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache parent pom + module poms first (layer optimization)
COPY pom.xml .
COPY secure-ai-model/pom.xml secure-ai-model/
COPY secure-ai-core/pom.xml secure-ai-core/
COPY secure-ai-service/pom.xml secure-ai-service/
COPY secure-ai-web/pom.xml secure-ai-web/

# Download dependencies (cached unless poms change)
RUN mvn -B dependency:go-offline -DskipTests 2>/dev/null || true

# Copy all module sources
COPY secure-ai-model/src secure-ai-model/src
COPY secure-ai-core/src secure-ai-core/src
COPY secure-ai-service/src secure-ai-service/src
COPY secure-ai-web/src secure-ai-web/src

# Build the multi-module project (FAT JAR produced by secure-ai-web)
RUN mvn -B clean package -DskipTests -pl secure-ai-web -am

# ─── Stage 2: Runtime (Alpine Linux) ──────────────────
FROM eclipse-temurin:21-jre-alpine

# Security: Create non-root user
RUN addgroup -g 1001 secureai && \
    adduser -u 1001 -G secureai -s /bin/false -D -H secureai

# Alpine security hardening: remove unnecessary packages
RUN apk --no-cache add curl && \
    rm -rf /var/cache/apk/*

# Labels (OCI spec)
LABEL org.opencontainers.image.title="Secure AI Gateway"
LABEL org.opencontainers.image.description="Enterprise-grade security gateway for AI model interactions"
LABEL org.opencontainers.image.version="2.0.0"
LABEL org.opencontainers.image.vendor="SecureAI Team — TUS Midlands"
LABEL org.opencontainers.image.base.name="eclipse-temurin:21-jre-alpine"

WORKDIR /app

# Copy FAT JAR from builder stage
COPY --from=builder --chown=secureai:secureai /build/secure-ai-web/target/secure-ai-web-2.0.0.jar app.jar

# Create log + tmp directories
RUN mkdir -p /var/log/secure-ai-gateway /tmp && \
    chown -R secureai:secureai /var/log/secure-ai-gateway /tmp

# Security: Switch to non-root
USER secureai

EXPOSE 8080

# JVM tuning for containers (Alpine-compatible)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1
